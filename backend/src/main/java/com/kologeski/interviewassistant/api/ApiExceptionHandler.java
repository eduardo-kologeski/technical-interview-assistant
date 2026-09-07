package com.kologeski.interviewassistant.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import com.kologeski.interviewassistant.auth.AuthenticationService;
import com.kologeski.interviewassistant.auth.IdentityToken;
import com.kologeski.interviewassistant.deployment.InstallationSetupService;
import com.kologeski.interviewassistant.interview.InterviewService;
import com.kologeski.interviewassistant.interview.PersonalAssessmentService;
import com.kologeski.interviewassistant.vacancy.JobVacancyService;
import com.kologeski.interviewassistant.company.CompanyContext;
import com.kologeski.interviewassistant.company.InvitationService;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(AuthenticationService.DuplicateEmailException.class)
    ProblemDetail duplicateEmail(AuthenticationService.DuplicateEmailException exception) {
        return problem(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(AuthenticationService.PublicSignupDisabledException.class)
    ProblemDetail publicSignupDisabled(AuthenticationService.PublicSignupDisabledException exception) {
        return problem(HttpStatus.FORBIDDEN, exception.getMessage());
    }

    @ExceptionHandler({InstallationSetupService.SetupUnavailableException.class,
            InstallationSetupService.UnsafeInstallationStateException.class})
    ProblemDetail setupUnavailable(RuntimeException exception) {
        return problem(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(InterviewService.StaleInterviewException.class)
    ProblemDetail staleInterview(InterviewService.StaleInterviewException exception) {
        return problem(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(PersonalAssessmentService.StalePersonalAssessmentException.class)
    ProblemDetail stalePersonalAssessment(PersonalAssessmentService.StalePersonalAssessmentException exception) {
        return problem(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler({JobVacancyService.StaleVacancyException.class, ObjectOptimisticLockingFailureException.class})
    ProblemDetail staleRecord(RuntimeException exception) {
        return problem(HttpStatus.CONFLICT,
                "Este registro foi alterado por outro usuário. Recarregue os dados.");
    }

    @ExceptionHandler(BadCredentialsException.class)
    ProblemDetail unauthorized(BadCredentialsException exception) {
        return problem(HttpStatus.UNAUTHORIZED, exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail invalid(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream().findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("Dados inválidos.");
        return problem(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler({IllegalArgumentException.class, ConstraintViolationException.class,
            HttpMessageNotReadableException.class, ServletRequestBindingException.class,
            MethodArgumentTypeMismatchException.class})
    ProblemDetail invalidRequest(Exception exception) {
        String detail = exception instanceof HttpMessageNotReadableException
                ? "O corpo da requisição está ausente ou possui formato inválido."
                : exception.getMessage();
        return problem(HttpStatus.BAD_REQUEST, detail);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ProblemDetail routeNotFound(NoResourceFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Recurso não encontrado.");
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail forbidden(AccessDeniedException exception) {
        return problem(HttpStatus.FORBIDDEN, "Acesso negado.");
    }

    @ExceptionHandler({CompanyContext.CompanyAccessDeniedException.class,
            InvitationService.InvitationEmailMismatchException.class,
            InvitationService.EmailVerificationRequiredException.class})
    ProblemDetail companyForbidden(RuntimeException exception) {
        return problem(HttpStatus.FORBIDDEN, exception.getMessage());
    }

    @ExceptionHandler(CompanyContext.CompanySelectionRequiredException.class)
    ProblemDetail companyRequired(CompanyContext.CompanySelectionRequiredException exception) {
        return problem(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(InvitationService.InvitationUnavailableException.class)
    ProblemDetail invitationNotFound(InvitationService.InvitationUnavailableException exception) {
        return problem(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(IdentityToken.IdentityTokenUnavailableException.class)
    ProblemDetail identityTokenNotFound(IdentityToken.IdentityTokenUnavailableException exception) {
        return problem(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail notFound(ResourceNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    ProblemDetail conflict(IllegalStateException exception) {
        return problem(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception exception) {
        log.error("event=unhandled_api_error type={} correlationId={}",
                exception.getClass().getSimpleName(), MDC.get("correlationId"));
        return problem(HttpStatus.INTERNAL_SERVER_ERROR,
                "Não foi possível concluir a operação. Tente novamente.");
    }

    private ProblemDetail problem(HttpStatus status, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        String correlationId = MDC.get("correlationId");
        if (correlationId != null) problem.setProperty("correlationId", correlationId);
        return problem;
    }
}
