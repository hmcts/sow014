package uk.gov.hmcts.divorce.divorcecase.model;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;
import uk.gov.hmcts.ccd.sdk.api.HasRole;

@AllArgsConstructor
@Getter
public enum UserRole implements HasRole {

    CASE_WORKER("caseworker-divorce-courtadmin_beta", "CRU"),
    CASE_WORKER_BULK_SCAN("caseworker-divorce-bulkscan", "CRU"),
    LEGAL_ADVISOR("caseworker-divorce-courtadmin-la", "CRU"),
    SUPER_USER("caseworker-divorce-superuser", "CRU"),
    SYSTEMUPDATE("caseworker-divorce-systemupdate", "CRUD"),
    JUDGE("caseworker-divorce-judge", "CRU"),
    NOC_APPROVER("caseworker-approver", "CRU"),

    SOLICITOR("caseworker-divorce-solicitor", "CRU"),
    APPLICANT_1_SOLICITOR("[APPONESOLICITOR]", "CRU"),
    APPLICANT_2_SOLICITOR("[APPTWOSOLICITOR]", "CRU"),
    ORGANISATION_CASE_ACCESS_ADMINISTRATOR("caseworker-caa", "CRU"),

    CITIZEN("citizen", "CRU"),
    CREATOR("[CREATOR]", "CRU"),
    APPLICANT_2("[APPLICANTTWO]", "CRU"),
    SOLICITOR_A("[SOLICITORA]", "CRU"),
    SOLICITOR_B("[SOLICITORB]", "CRU"),
    SOLICITOR_C("[SOLICITORC]", "CRU"),
    SOLICITOR_D("[SOLICITORD]", "CRU"),
    SOLICITOR_E("[SOLICITORE]", "CRU"),
    SOLICITOR_F("[SOLICITORF]", "CRU"),
    SOLICITOR_G("[SOLICITORG]", "CRU"),
    SOLICITOR_H("[SOLICITORH]", "CRU"),
    SOLICITOR_I("[SOLICITORI]", "CRU"),
    SOLICITOR_J("[SOLICITORJ]", "CRU");

    @JsonValue
    private final String role;
    private final String caseTypePermissions;

    public static UserRole fromString(String value) {
        for (UserRole role : UserRole.values()) {
            if (role.getRole().equals(value)) {
                return role;
            }
        }
        return null;
    }

}
