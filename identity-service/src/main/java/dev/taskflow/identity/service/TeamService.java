package dev.taskflow.identity.service;

import dev.taskflow.identity.domain.AppUser;
import dev.taskflow.identity.domain.Organization;
import dev.taskflow.identity.domain.Team;
import dev.taskflow.identity.repo.AppUserRepository;
import dev.taskflow.identity.repo.OrganizationRepository;
import dev.taskflow.identity.repo.TeamRepository;
import dev.taskflow.identity.security.TokenClaims;
import dev.taskflow.identity.web.ApiException;
import dev.taskflow.identity.web.Dtos.AddTeamMemberRequest;
import dev.taskflow.identity.web.Dtos.CreateTeamRequest;
import dev.taskflow.identity.web.Dtos.TeamResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class TeamService {

    private final TeamRepository teams;
    private final AppUserRepository users;
    private final OrganizationRepository organizations;

    public TeamService(TeamRepository teams,
                       AppUserRepository users,
                       OrganizationRepository organizations) {
        this.teams = teams;
        this.users = users;
        this.organizations = organizations;
    }

    @Transactional(readOnly = true)
    public List<TeamResponse> list(TokenClaims claims) {
        return teams.findAllByOrganizationIdOrderByNameAsc(claims.orgId())
                .stream()
                .map(TeamResponse::from)
                .toList();
    }

    @Transactional
    public TeamResponse create(TokenClaims claims, CreateTeamRequest request) {
        Organization org = organizations.findById(claims.orgId())
                .orElseThrow(() -> ApiException.notFound("That workspace no longer exists."));
        Team team = teams.save(Team.of(org, request.name().trim(), claims.userId()));
        return TeamResponse.from(team);
    }

    @Transactional
    public TeamResponse addMember(TokenClaims claims, UUID teamId,
                                  AddTeamMemberRequest request) {
        Team team = teams.findById(teamId)
                .orElseThrow(() -> ApiException.notFound("That team no longer exists."));
        if (!team.getOrganization().getId().equals(claims.orgId())) {
            throw ApiException.notFound("That team no longer exists.");
        }
        AppUser user = users.findById(request.userId())
                .orElseThrow(() -> ApiException.notFound("That member no longer exists."));
        if (!user.getOrganization().getId().equals(claims.orgId())) {
            throw ApiException.notFound("That member no longer exists.");
        }
        team.getMemberIds().add(user.getId());
        return TeamResponse.from(team);
    }
}
