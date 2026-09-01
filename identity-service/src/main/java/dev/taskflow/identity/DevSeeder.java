package dev.taskflow.identity;

import dev.taskflow.identity.domain.AppUser;
import dev.taskflow.identity.domain.Organization;
import dev.taskflow.identity.domain.Role;
import dev.taskflow.identity.domain.Team;
import dev.taskflow.identity.repo.AppUserRepository;
import dev.taskflow.identity.repo.OrganizationRepository;
import dev.taskflow.identity.repo.TeamRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@Configuration
@ConditionalOnProperty(name = "taskflow.seed", havingValue = "true")
public class DevSeeder {

    private static final Logger log = LoggerFactory.getLogger(DevSeeder.class);

    @Bean
    @Transactional
    public ApplicationRunner seedData(OrganizationRepository organizations,
                                      AppUserRepository users,
                                      TeamRepository teams,
                                      PasswordEncoder encoder) {
        return args -> {
            if (organizations.count() > 0) {
                log.info("Seed skipped: workspace already exists");
                return;
            }
            Organization org = organizations.save(Organization.of("Northwind Labs"));

            AppUser admin = users.save(AppUser.create(org, "admin@taskflow.dev",
                    encoder.encode("admin123"), "Asha Rao", Role.ADMIN, "Asia/Kolkata"));
            AppUser dev1 = users.save(AppUser.create(org, "dev1@taskflow.dev",
                    encoder.encode("member123"), "Rohit Menon", Role.MEMBER, "Asia/Kolkata"));
            AppUser dev2 = users.save(AppUser.create(org, "dev2@taskflow.dev",
                    encoder.encode("member123"), "Priya Nair", Role.MEMBER, "Asia/Kolkata"));

            Team team = Team.of(org, "Core platform", admin.getId());
            team.getMemberIds().add(dev1.getId());
            team.getMemberIds().add(dev2.getId());
            teams.save(team);

            log.info("Seeded workspace '{}' with 3 users", org.getName());
        };
    }
}

