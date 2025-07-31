package com.edu.edumeet;

import lombok.Cleanup;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.Assertions;
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
@ActiveProfiles("test") //H2 사용
public class H2DataSourceTests {

    @Autowired
    private DataSource dataSource;

    @Test
    public void 테스트_H2연결되는지() throws SQLException {

        @Cleanup
        Connection con = dataSource.getConnection();


        DatabaseMetaData metaData = con.getMetaData();
        String databaseProductName = metaData.getDatabaseProductName();

        log.info("========= H2 데이터베이스 연결 테스트 ========");
        log.info("데이터베이스 제품명 : " + databaseProductName);
        log.info("URL : " + metaData.getURL());

        //H2 데이터베이스인지 확인
        if ( databaseProductName.toLowerCase().contains("h2") ) {
            log.info("H2 데이터베이스 연결 성공!");
            Assertions.assertTrue(true);
        } else{
            log.info("H2 디비가 아님. 현재 : " + databaseProductName);
            Assertions.fail("H2 DB 아님");
        }

        Assertions.assertNotNull(con);
    }
}
