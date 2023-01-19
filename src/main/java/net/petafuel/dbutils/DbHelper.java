package net.petafuel.dbutils;

import com.mysql.cj.jdbc.MysqlDataSource;
import net.petafuel.fuelifints.dataaccess.Banking2Access;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.naming.NamingException;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.*;
import java.util.Properties;

public class DbHelper {
    private static final Logger LOG = LogManager.getLogger(DbHelper.class);

    private static MysqlDataSource dataSource = null;

    static {
        try {
            Properties fuelifintsProperties = new Properties();
            fuelifintsProperties.load(new FileInputStream("config/fuelifints.properties"));

            String bankcode = fuelifintsProperties.getProperty("bankcode", "12345678");

            Properties connProperties = new Properties();
            connProperties.load(new FileInputStream("connectionpool.properties"));

            String driverClass = connProperties.getProperty("db.driverclass", "com.mysql.cj.jdbc.Driver");
            Class.forName(driverClass);

            dataSource = new MysqlDataSource();
            dataSource.setServerName(connProperties.getProperty("db.server." + bankcode, "127.0.0.1"));
            dataSource.setDatabaseName(connProperties.getProperty("db.name." + bankcode, "fints"));
            dataSource.setUser(connProperties.getProperty("db.user." + bankcode, "fintsuser"));
            dataSource.setPassword(connProperties.getProperty("db.password." + bankcode));

            dataSource.setUseSSL(false);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");

            System.out.println("Connecting to server...");

            try(Connection connection = dataSource.getConnection()) {
                System.out.println("Server connected!");
                Statement stmt = null;
                ResultSet resultset = null;

                try {
                    stmt = connection.createStatement();
                    resultset = stmt.executeQuery("SHOW DATABASES;");

                    if (stmt.execute("SHOW DATABASES;")) {
                        resultset = stmt.getResultSet();
                    }

                    while (resultset.next()) {
                        System.out.println(resultset.getString("Database"));
                    }
                } catch (SQLException ex) {
                    // handle any errors
                    ex.printStackTrace();
                } finally {
                    // release resources
                    if (resultset != null) {
                        try {
                            resultset.close();
                        } catch (SQLException sqlEx) {
                        }
                        resultset = null;
                    }

                    if (stmt != null) {
                        try {
                            stmt.close();
                        } catch (SQLException sqlEx) {
                        }
                        stmt = null;
                    }

                    if (connection != null) {
                        connection.close();
                    }
                }
            } catch (SQLException e) {
                System.out.println(e.getMessage());
                throw new IllegalStateException("Cannot connect the server!", e);

            }
        } catch (Exception e) {}
    }

    public Connection getConnection(String dbprofile) throws NamingException {
        try {
            System.out.println("Connecting to server...");
            return dataSource.getConnection();
        } catch (Exception e) {
            e.printStackTrace();
            throw new NamingException(e.getMessage());
        }
    }

    public void closeQuietly(Connection connection) {
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (Exception e) {
            LOG.error("Exception: %s", connection.toString());
        }
    }

    public void closeQuietly(ResultSet resultSet) {
        try {
            if (resultSet != null) {
                resultSet.close();
            }
        } catch (Exception e) {
            LOG.error("Exception: %s", resultSet.toString());
        }
    }

    public void closeQuietly(PreparedStatement pSi) {
        try {
            if (pSi != null) {
                pSi.close();
            }
        } catch (Exception e) {
            LOG.error("Exception: %s", pSi.toString());
        }
    }
}
