package kn.jdb;

import kn.jdb.datasource.DataSourceProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.server.ResponseStatusException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
class DbBrowserApplicationTests {

	@MockitoBean
	private DataSource dataSource;

	private final DataSourceProvider dataSourceProvider = environment -> dataSource;

	@Test
	void contextLoads() {
	}

	@Test
	void getRowsReturnsPaginatedRows() throws Exception {
		var controller = new kn.jdb.controllers.DatabasesController(dataSourceProvider);
		Connection connection = mock(Connection.class);
		PreparedStatement statement = mock(PreparedStatement.class);
		ResultSet resultSet = mock(ResultSet.class);
		ResultSetMetaData resultSetMetaData = mock(ResultSetMetaData.class);
		DatabaseMetaData databaseMetaData = mock(DatabaseMetaData.class);

		when(dataSource.getConnection()).thenReturn(connection);
		when(connection.getMetaData()).thenReturn(databaseMetaData);
		when(databaseMetaData.getIdentifierQuoteString()).thenReturn("\"");
		when(connection.getSchema()).thenReturn("public");
		when(connection.prepareStatement("SELECT * FROM \"public\".\"users\" LIMIT ? OFFSET ?")).thenReturn(statement);
		when(statement.executeQuery()).thenReturn(resultSet);
		when(resultSet.getMetaData()).thenReturn(resultSetMetaData);
		when(resultSetMetaData.getColumnCount()).thenReturn(2);
		when(resultSetMetaData.getColumnLabel(1)).thenReturn("id");
		when(resultSetMetaData.getColumnLabel(2)).thenReturn("name");
		when(resultSet.next()).thenReturn(true, true, false);
		when(resultSet.getObject(1)).thenReturn(1, 2);
		when(resultSet.getObject(2)).thenReturn("Ada", "Grace");

		Map<String, Object> response = controller.getRows("demo", "users", 1, 2, null, null);

		assertThat(response.get("page")).isEqualTo(1);
		assertThat(response.get("count")).isEqualTo(2);
		assertThat(response.get("rows")).isInstanceOf(List.class);

		@SuppressWarnings("unchecked")
		List<Map<String, Object>> rows = (List<Map<String, Object>>) response.get("rows");
		assertThat(rows).hasSize(2);
		assertThat(rows.get(0).get("id")).isEqualTo(1);
		assertThat(rows.get(0).get("name")).isEqualTo("Ada");
		assertThat(rows.get(1).get("id")).isEqualTo(2);
		assertThat(rows.get(1).get("name")).isEqualTo("Grace");
	}

	@Test
	void invalidPaginationReturnsBadRequest() throws Exception {
		var handler = new kn.jdb.controllers.ApiExceptionHandler();

		IllegalArgumentException exception = catchThrowableOfType(IllegalArgumentException.class, () -> new kn.jdb.controllers.DatabasesController(dataSourceProvider).getRows("demo", "users", -1, 10, null, null));

		ProblemDetail problemDetail = handler.handleIllegalArgumentException(exception);
		assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
		assertThat(problemDetail.getDetail()).isEqualTo("page must be greater than or equal to 0");
	}

	@Test
	void sqlErrorsReturnClientVisibleHttpStatus() throws Exception {
		var controller = new kn.jdb.controllers.DatabasesController(dataSourceProvider);
		var handler = new kn.jdb.controllers.ApiExceptionHandler();
		Connection connection = mock(Connection.class);
		PreparedStatement statement = mock(PreparedStatement.class);
		DatabaseMetaData databaseMetaData = mock(DatabaseMetaData.class);

		when(dataSource.getConnection()).thenReturn(connection);
		when(connection.getMetaData()).thenReturn(databaseMetaData);
		when(databaseMetaData.getIdentifierQuoteString()).thenReturn("\"");
		when(connection.getSchema()).thenReturn("public");
		when(connection.prepareStatement("SELECT * FROM \"public\".\"users\" LIMIT ? OFFSET ?")).thenReturn(statement);
		when(statement.executeQuery()).thenThrow(new java.sql.SQLException("relation \"users\" does not exist"));

		ResponseStatusException exception = catchThrowableOfType(ResponseStatusException.class, () -> controller.getRows("demo", "users", 0, 10, null, null));

		ProblemDetail problemDetail = handler.handleResponseStatusException(exception);
		assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
		assertThat(problemDetail.getDetail()).isEqualTo("Failed to fetch rows for table users");
		assertThat(problemDetail.getProperties().get("error").toString()).contains("relation \"users\" does not exist");
	}

	@Test
	void invalidRawSqlReturnsBadRequest() throws Exception {
		var controller = new kn.jdb.controllers.RawSqlController(dataSourceProvider);
		var handler = new kn.jdb.controllers.ApiExceptionHandler();
		Connection connection = mock(Connection.class);
		java.sql.Statement statement = mock(java.sql.Statement.class);

		when(dataSource.getConnection()).thenReturn(connection);
		when(connection.createStatement()).thenReturn(statement);
		when(statement.execute("select from")).thenThrow(new java.sql.SQLException("syntax error at or near \"from\""));

		ResponseStatusException exception = catchThrowableOfType(ResponseStatusException.class, () -> controller.executeRawSql(null, null, null, "select from"));

		ProblemDetail problemDetail = handler.handleResponseStatusException(exception);
		assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
		assertThat(problemDetail.getDetail()).isEqualTo("Failed to execute SQL");
		assertThat(problemDetail.getProperties().get("error").toString()).contains("syntax error");
	}

	@Test
	void rawSqlExecutesOnlyFirstStatementIgnoringSemicolonsInComments() throws Exception {
		var controller = new kn.jdb.controllers.RawSqlController(dataSourceProvider);
		Connection connection = mock(Connection.class);
		java.sql.Statement statement = mock(java.sql.Statement.class);
		String firstStatement = "select 1";

		when(dataSource.getConnection()).thenReturn(connection);
		when(connection.createStatement()).thenReturn(statement);
		when(statement.execute(firstStatement)).thenReturn(false);
		when(statement.getUpdateCount()).thenReturn(1);

		Object result = controller.executeRawSql(
				null,
				null,
				null,
				"-- ignored; comment\n" + firstStatement + "; select 2"
		);

		verify(statement).execute(firstStatement);
		assertThat(result).isEqualTo(Map.of("updateCount", 1));
	}

}
