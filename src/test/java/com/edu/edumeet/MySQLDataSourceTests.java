package com.edu.edumeet;


import lombok.Cleanup;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

@SpringBootTest
@Log4j2
@ActiveProfiles("prod")
@Tag("mysql")
public class MySQLDataSourceTests {

    @Autowired
    private DataSource dataSource;

    @Test
    public void 테스트_MySQL연결되는지() throws SQLException{

        @Cleanup
        Connection con = dataSource.getConnection();

        DatabaseMetaData metaData = con.getMetaData();
        String databaseProductName = metaData.getDatabaseProductName();

        log.info("=== MYSQL 데이터베이스 연결 테스트 ===");
        log.info("데이터베이스 : " + databaseProductName);
        log.info("URL : " + metaData.getURL());


        //mysql 디비인지 확인
        if(databaseProductName.toLowerCase().contains("mysql") ) {
            log.info("MYSQL DB 연결 성공!");
            Assertions.assertTrue(true);
        } else{
            log.info("MYSQL DB가 아닙니다. 현재 : " + databaseProductName);
            Assertions.fail("MYSQL 데이터베이스가 아닙니다.");
        }

        Assertions.assertNotNull(con);
    }
}
