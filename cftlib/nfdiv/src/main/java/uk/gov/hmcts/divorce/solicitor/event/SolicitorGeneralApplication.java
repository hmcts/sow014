package uk.gov.hmcts.divorce.solicitor.event;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.nfdiv.civil.tables.Payment;
import org.jooq.nfdiv.civil.tables.records.PaymentRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.ccd.sdk.api.CCDConfig;
import uk.gov.hmcts.ccd.sdk.api.CaseDetails;
import uk.gov.hmcts.ccd.sdk.api.ConfigBuilder;
import uk.gov.hmcts.ccd.sdk.api.callback.AboutToStartOrSubmitResponse;
import uk.gov.hmcts.ccd.sdk.type.ListValue;
import uk.gov.hmcts.ccd.sdk.type.OrderSummary;
import uk.gov.hmcts.ccd.sdk.type.YesOrNo;
import uk.gov.hmcts.divorce.common.ccd.CcdPageConfiguration;
import uk.gov.hmcts.divorce.common.ccd.PageBuilder;
import uk.gov.hmcts.divorce.common.event.page.GeneralApplicationSelectApplicationType;
import uk.gov.hmcts.divorce.common.event.page.GeneralApplicationUploadDocument;
import uk.gov.hmcts.divorce.divorcecase.model.CaseData;
import uk.gov.hmcts.divorce.divorcecase.model.GeneralApplication;
import uk.gov.hmcts.divorce.divorcecase.model.PaymentStatus;
import uk.gov.hmcts.divorce.divorcecase.model.Solicitor;
import uk.gov.hmcts.divorce.divorcecase.model.State;
import uk.gov.hmcts.divorce.divorcecase.model.UserRole;
import uk.gov.hmcts.divorce.payment.PaymentService;
import uk.gov.hmcts.divorce.payment.model.PbaResponse;
import uk.gov.hmcts.divorce.solicitor.client.organisation.OrganisationClient;
import uk.gov.hmcts.divorce.solicitor.event.page.GeneralApplicationPaymentConfirmation;
import uk.gov.hmcts.divorce.solicitor.event.page.GeneralApplicationPaymentSummary;
import uk.gov.hmcts.divorce.solicitor.event.page.GeneralApplicationSelectFee;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.Objects.isNull;
import static org.apache.commons.lang3.ObjectUtils.isEmpty;
import static org.jooq.nfdiv.civil.tables.Payment.PAYMENT;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpStatus.CREATED;
import static uk.gov.hmcts.divorce.divorcecase.model.PaymentStatus.SUCCESS;
import static uk.gov.hmcts.divorce.divorcecase.model.State.Applicant2Approved;
import static uk.gov.hmcts.divorce.divorcecase.model.State.Archived;
import static uk.gov.hmcts.divorce.divorcecase.model.State.AwaitingApplicant1Response;
import static uk.gov.hmcts.divorce.divorcecase.model.State.AwaitingApplicant2Response;
import static uk.gov.hmcts.divorce.divorcecase.model.State.AwaitingPayment;
import static uk.gov.hmcts.divorce.divorcecase.model.State.AwaitingPronouncement;
import static uk.gov.hmcts.divorce.divorcecase.model.State.Draft;
import static uk.gov.hmcts.divorce.divorcecase.model.State.GeneralApplicationReceived;
import static uk.gov.hmcts.divorce.divorcecase.model.State.Rejected;
import static uk.gov.hmcts.divorce.divorcecase.model.State.Withdrawn;
import static uk.gov.hmcts.divorce.divorcecase.model.UserRole.APPLICANT_1_SOLICITOR;
import static uk.gov.hmcts.divorce.divorcecase.model.UserRole.APPLICANT_2_SOLICITOR;
import static uk.gov.hmcts.divorce.divorcecase.model.UserRole.CASE_WORKER;
import static uk.gov.hmcts.divorce.divorcecase.model.UserRole.JUDGE;
import static uk.gov.hmcts.divorce.divorcecase.model.UserRole.LEGAL_ADVISOR;
import static uk.gov.hmcts.divorce.divorcecase.model.UserRole.SUPER_USER;
import static uk.gov.hmcts.divorce.divorcecase.model.access.Permissions.CREATE_READ_UPDATE_DELETE;

@Slf4j
@Component
public class SolicitorGeneralApplication implements CCDConfig<CaseData, State, UserRole> {

    public static final String SOLICITOR_GENERAL_APPLICATION = "solicitor-general-application";
    private static final String GENERAL_APPLICATION = "General Application";
    private static final String GENERAL_APPLICATION_BULK_CASE_ERROR =
        "General Application cannot be submitted as this case is currently linked to an active bulk action case";
    private static final String GENERAL_APPLICATION_ORG_POLICY_ERROR =
        "General Application payment could not be completed as the invokers organisation policy did not match any on the case";
    private static final String GENERAL_APPLICATION_URGENT_CASE_REASON_ERROR =
        "General Application marked as urgent need an accompanying reason why it is urgent";

    private static final EnumSet<State> GENERAL_APPLICATION_STATES = EnumSet.complementOf(EnumSet.of(
        Draft,
        AwaitingApplicant1Response,
        AwaitingApplicant2Response,
        Applicant2Approved,
        AwaitingPayment,
        Withdrawn,
        Rejected,
        Archived
    ));

    @Autowired
    private GeneralApplicationSelectFee generalApplicationSelectFee;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private OrganisationClient organisationClient;

    @Autowired
    private HttpServletRequest request;

    @Autowired
    private AuthTokenGenerator authTokenGenerator;

    @Autowired
    private DSLContext db;

    @Override
    public void configure(final ConfigBuilder<CaseData, State, UserRole> configBuilder) {
        final PageBuilder pageBuilder = addEventConfig(configBuilder);

        final List<CcdPageConfiguration> pages = asList(
            new GeneralApplicationSelectApplicationType(),
            generalApplicationSelectFee,
            new GeneralApplicationUploadDocument(),
            new GeneralApplicationPaymentConfirmation(),
            new GeneralApplicationPaymentSummary()
        );

        pages.forEach(page -> page.addTo(pageBuilder));
    }

    public AboutToStartOrSubmitResponse<CaseData, State> aboutToStart(final CaseDetails<CaseData, State> details) {

        log.info("{} about to start callback invoked for Case Id: {}", SOLICITOR_GENERAL_APPLICATION, details.getId());
        final CaseData data = details.getData();

        data.setGeneralApplication(GeneralApplication.builder().build());

        return AboutToStartOrSubmitResponse.<CaseData, State>builder()
            .data(data)
            .build();
    }

    public AboutToStartOrSubmitResponse<CaseData, State> aboutToSubmit(final CaseDetails<CaseData, State> details,
                                                                       final CaseDetails<CaseData, State> beforeDetails) {

        log.info("{} about to submit callback invoked for Case Id: {}", SOLICITOR_GENERAL_APPLICATION, details.getId());
        final CaseData data = details.getData();

        if (AwaitingPronouncement == details.getState() && !isNull(data.getBulkListCaseReferenceLink())
            && !isEmpty(data.getBulkListCaseReferenceLink().getCaseReference())) {

            return AboutToStartOrSubmitResponse.<CaseData, State>builder()
                .errors(singletonList(GENERAL_APPLICATION_BULK_CASE_ERROR))
                .build();
        }

        final GeneralApplication generalApplication = data.getGeneralApplication();

        if (generalApplication.getGeneralApplicationUrgentCase() == YesOrNo.YES) {
            if (isEmpty(generalApplication.getGeneralApplicationUrgentCaseReason())) {
                return AboutToStartOrSubmitResponse.<CaseData, State>builder()
                    .errors(singletonList(GENERAL_APPLICATION_URGENT_CASE_REASON_ERROR))
                    .build();
            }
        }

        if (generalApplication.getGeneralApplicationFee().isPaymentMethodPba()) {
            final Solicitor invokingSolicitor = getInvokingSolicitor(data, request.getHeader(AUTHORIZATION));

            if (isNull(invokingSolicitor)) {
                return AboutToStartOrSubmitResponse.<CaseData, State>builder()
                    .data(details.getData())
                    .errors(singletonList(GENERAL_APPLICATION_ORG_POLICY_ERROR))
                    .build();
            }

            final PbaResponse response = paymentService.processPbaPayment(
                data,
                details.getId(),
                invokingSolicitor,
                generalApplication.getGeneralApplicationFee().getPbaNumber(),
                generalApplication.getGeneralApplicationFee().getOrderSummary(),
                generalApplication.getGeneralApplicationFee().getAccountReferenceNumber()
            );

            final OrderSummary generalApplicationFeeOrderSummary = generalApplication.getGeneralApplicationFee().getOrderSummary();

            if (response.getHttpStatus() == CREATED) {
                PaymentRecord paymentRecord = db.newRecord(Payment.PAYMENT);
                paymentRecord.setAmount(new BigDecimal(generalApplicationFeeOrderSummary.getPaymentTotal()));
                paymentRecord.setChannel("online");
                paymentRecord.setFeeCode(generalApplicationFeeOrderSummary.getFees().get(0).getValue().getCode());
                paymentRecord.setReference(response.getPaymentReference());
                paymentRecord.setStatus(SUCCESS.getLabel());
                paymentRecord.setId(UUID.randomUUID().toString());
                paymentRecord.setCaseReference(details.getId());
                paymentRecord.setCreated(LocalDateTime.now());
                paymentRecord.store();
                data.getApplication().setApplicationPayments(getApplicationPayments(details.getId()));
//                data.updateCaseDataWithPaymentDetails(generalApplicationFeeOrderSummary, data, response.getPaymentReference());
            } else {
                return AboutToStartOrSubmitResponse.<CaseData, State>builder()
                    .data(details.getData())
                    .errors(singletonList(response.getErrorMessage()))
                    .build();
            }
        }

        data.updateCaseWithGeneralApplication();

        return AboutToStartOrSubmitResponse.<CaseData, State>builder()
            .data(data)
            .state(GeneralApplicationReceived)
            .build();
    }

    private List<ListValue<uk.gov.hmcts.divorce.divorcecase.model.Payment>> getApplicationPayments(long caseRef) {
        return db.fetch(PAYMENT, PAYMENT.CASE_REFERENCE.eq(caseRef)).stream().map(created ->
                ListValue.<uk.gov.hmcts.divorce.divorcecase.model.Payment>builder()
                    .id(created.getId())
                    .value(uk.gov.hmcts.divorce.divorcecase.model.Payment.builder()
                        .reference(created.getReference())
                        .created(created.getCreated())
                        .amount(created.getAmount().intValue())
                        .serviceRequestReference(created.getServiceRequestReference())
                        .transactionId(created.getTransactionId())
                        .channel(created.getChannel())
                        .status(PaymentStatus.fromLabel(created.getStatus()))
                        .feeCode(created.getFeeCode())
                        .build())
                    .build())
            .toList();
    }

    private Solicitor getInvokingSolicitor(final CaseData caseData, final String userAuth) {

        if (!caseData.getApplicant2().isRepresented()) {
            return caseData.getApplicant1().getSolicitor();
        }

        if (!caseData.getApplicant1().isRepresented()) {
            return caseData.getApplicant2().getSolicitor();
        }

        String applicant1SolicitorSelectedOrgId =
            Objects.requireNonNull(caseData
                    .getApplicant1()
                    .getSolicitor()
                    .getOrganisationPolicy())
                .getOrganisation()
                .getOrganisationId();

        String applicant2SolicitorSelectedOrgId =
            Objects.requireNonNull(caseData
                    .getApplicant2()
                    .getSolicitor()
                    .getOrganisationPolicy())
                .getOrganisation()
                .getOrganisationId();

        String solicitorUserOrgId = organisationClient
            .getUserOrganisation(userAuth, authTokenGenerator.generate())
            .getOrganisationIdentifier();

        if (solicitorUserOrgId.equalsIgnoreCase(applicant1SolicitorSelectedOrgId)) {
            return caseData.getApplicant1().getSolicitor();
        } else if (solicitorUserOrgId.equalsIgnoreCase(applicant2SolicitorSelectedOrgId)) {
            return caseData.getApplicant2().getSolicitor();
        } else {
            return null;
        }
    }

    private PageBuilder addEventConfig(final ConfigBuilder<CaseData, State, UserRole> configBuilder) {

        return new PageBuilder(configBuilder
            .event(SOLICITOR_GENERAL_APPLICATION)
            .forStates(GENERAL_APPLICATION_STATES)
            .name(GENERAL_APPLICATION)
            .description(GENERAL_APPLICATION)
            .showSummary()
            .showEventNotes()
            .aboutToStartCallback(this::aboutToStart)
            .aboutToSubmitCallback(this::aboutToSubmit)
            .endButtonLabel("Submit Application")
            .grant(CREATE_READ_UPDATE_DELETE, APPLICANT_1_SOLICITOR, APPLICANT_2_SOLICITOR)
            .grantHistoryOnly(CASE_WORKER, SUPER_USER, LEGAL_ADVISOR, JUDGE));
    }
}
