package com.yz.datasources;

import com.yz.common.core.utils.StringUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import javax.annotation.Resource;
import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class BaseDao {

	public static final Logger logger= LogManager.getLogger(BaseDao.class);

	@Resource(name = "jdbcTemplate")
	private JdbcTemplate jdbcTemplate;

	public void setJdbcTemplate(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public JdbcTemplate getJdbcTemplate() {
		return jdbcTemplate;
	}

	@Resource(name = "sqlSession")
	private SqlSession sqlSession;

	@Resource(name = "sqlSessionFactory")
	private SqlSessionFactory sqlSessionFactory;


	/**
	 * 获取sqlsession对象
	 *
	 * @return
	 */
	public SqlSession getSqlSession() {
		return sqlSession;
	}

	public SqlSession getSqlSession(boolean status) {
		SqlSession sqlSession = sqlSessionFactory.openSession(status);
		return sqlSession;
	}

	/**
	 * 插入一条数据，并返回自增id
	 *
	 * @param sql
	 * @return
	 */
	public int insert(String sql,Object...objects) {
		int autoIncId = 0;
		KeyHolder keyHolder = new GeneratedKeyHolder();
		jdbcTemplate.update(new PreparedStatementCreator() {
			@Override
			public PreparedStatement createPreparedStatement(Connection con) throws SQLException {
				PreparedStatement ps = (PreparedStatement) con.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS);
				for(int i=0;i<objects.length;i++){
					ps.setObject(i+1, objects[i]);
				}
				return ps;
			}
		}, keyHolder);
		autoIncId = keyHolder.getKey().intValue(); // 获取自增ID
		return autoIncId;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public List<Map<String, Object>> execute(String callString, Object... params) {
		return (List<Map<String, Object>>) jdbcTemplate.execute(callString, new CallableStatementCallback() {

			@Override
			public List<Map<String, Object>> doInCallableStatement(CallableStatement cs) throws SQLException, DataAccessException {
				for(int i=0;i<params.length;i++){
					cs.setObject(i+1, params[i]);
				}
				// 执行存储过程
				boolean hadResults = cs.execute();
				List<Map<String, Object>> list = new ArrayList<>();
				while (hadResults) {
					ResultSet rs = cs.getResultSet();
					while (rs != null && rs.next()) {
						ColumnMapRowMapper mapper = new ColumnMapRowMapper();
						Map<String, Object> map = mapper.mapRow(rs, 0);
						list.add(map);
					}
					hadResults = cs.getMoreResults();
				}
				return list;
			}
		});
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public <T> List<T> execute(String callString, Class<T> clazz, Object... params) {
		return (List<T>) jdbcTemplate.execute(callString, new CallableStatementCallback() {

			@Override
			public List<T> doInCallableStatement(CallableStatement cs) throws SQLException, DataAccessException {
				for(int i=0;i<params.length;i++){
					cs.setObject(i+1, params[i]);
				}
				// 执行存储过程
				boolean hadResults = cs.execute();
				List<T> list = new ArrayList<>();
				while (hadResults) {
					ResultSet rs = cs.getResultSet();
					T t;
					try {
						t = clazz.newInstance();
					} catch (Exception e) {
						e.printStackTrace();
					}
					BeanPropertyRowMapper mapper = new BeanPropertyRowMapper();
					t = (T) mapper.mapRow(rs, 0);
					list.add(t);
					hadResults = cs.getMoreResults();
				}
				return list;
			}
		});
	}

	@SuppressWarnings("rawtypes")
	public String createQuerySql(String tableName, Class clazz) {
		String sql = null;
		StringBuilder builder = new StringBuilder();
		Field[] fields = clazz.getDeclaredFields();
		for (Field field : fields) {
			String fieldName = field.getName();
			fieldName = StringUtils.addCharByIndexFront(fieldName, "_");
			builder.append(fieldName).append(",");
		}
		String property = builder.toString();
		sql = "select " + property.substring(0, property.length() - 1) + " from " + tableName + " " + sql;
		return sql;
	}

	public <T> T queryForObject(String sql, Class<T> clazz, Object... paramValue) {
		try {
			RowMapper<T> rowMapper = new BeanPropertyRowMapper<T>(clazz);
			return (T) jdbcTemplate.queryForObject(sql, rowMapper, paramValue);
		} catch (EmptyResultDataAccessException e) {
			logger.error("查询数据为空");
		}
		return null;
	}

	public <T> List<T> queryForObjectList(String sql, Class<T> clazz, Object ... paramValue) {
		try {
			RowMapper<T> rowMapper = new BeanPropertyRowMapper<T>(clazz);
			return jdbcTemplate.query(sql, paramValue, rowMapper);
		} catch (Exception e) {
			logger.error("查询失败 ----", e);
		}
		return null;
	}

	/**
	 * 获取数据源
	 * @return
	 */
	public DataSource getDataSource() {
		return jdbcTemplate.getDataSource();
	}

	/**
	 * 执行sql语句
	 * @param sql
	 */
	public void query(String sql) {
		Connection con = null;
		try {
			con = this.getDataSource().getConnection();
			PreparedStatement ps = con.prepareStatement(sql);
			ps.execute();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	/**
	 * spring手动事务回滚
	 */
	public void rollBack(){
		TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
	}
}
