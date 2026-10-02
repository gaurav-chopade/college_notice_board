package com.college.noticeboard.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * Upgrades databases created before Engineering/Polytechnic and public notices existed:
 * <ul>
 *     <li>existing departments become Engineering departments;</li>
 *     <li>existing notices become private (as they always were);</li>
 *     <li>on MySQL, drops the old "department name is unique" index, because Engineering and
 *         Polytechnic may each have a department with the same name. Uniqueness is now per wing.</li>
 * </ul>
 * Each step does nothing once applied.
 */
@Slf4j
@Component
@Order(0)
@RequiredArgsConstructor
public class InstitutionUpgrade implements ApplicationRunner {

    private final JdbcTemplate jdbc;

    @Override
    public void run(ApplicationArguments args) {
        int depts = jdbc.update("UPDATE departments SET institution_type = 'ENGINEERING' WHERE institution_type IS NULL");
        int notices = jdbc.update("UPDATE notices SET visibility = 'PRIVATE' WHERE visibility IS NULL");
        if (depts + notices > 0) {
            log.info("Upgraded {} department(s) to Engineering and {} notice(s) to private", depts, notices);
        }
        dropOldDepartmentNameIndex();
    }

    private void dropOldDepartmentNameIndex() {
        try {
            String product = jdbc.execute((ConnectionCallback<String>) c -> c.getMetaData().getDatabaseProductName());
            if (product == null || !(product.toLowerCase(Locale.ROOT).contains("mysql") || product.toLowerCase(Locale.ROOT).contains("mariadb"))) {
                return;
            }
            List<String> indexes = jdbc.queryForList(
                    "SELECT index_name FROM information_schema.statistics WHERE table_schema = DATABASE() "
                            + "AND table_name = 'departments' AND non_unique = 0 AND index_name <> 'PRIMARY' "
                            + "GROUP BY index_name HAVING COUNT(*) = 1 AND MAX(column_name) = 'name'", String.class);
            for (String index : indexes) {
                jdbc.execute("ALTER TABLE departments DROP INDEX `" + index.replace("`", "") + "`");
                log.info("Dropped old unique index {} on departments.name (names are now unique per institution)", index);
            }
        } catch (RuntimeException e) {
            log.warn("Could not check the old departments.name index: {}", e.getMessage());
        }
    }
}
