package com.example.valueinsoftbackend.Service.openitems;

import com.example.valueinsoftbackend.Model.OpenItems.OpenItemsWriteModels;
import io.micrometer.core.instrument.Timer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/** Keeps instrumentation out of the transactional allocation protocol itself. */
@Aspect
@Component
public class OpenItemsMetricsAspect {

    private final OpenItemsMetrics metrics;

    public OpenItemsMetricsAspect(OpenItemsMetrics metrics) {
        this.metrics = metrics;
    }

    @Around("execution(* com.example.valueinsoftbackend.Service.openitems.ArOpenItemService.allocateReceipt(..))")
    public Object arReceipt(ProceedingJoinPoint joinPoint) throws Throwable {
        return observe(joinPoint, "AR", "receipt");
    }

    @Around("execution(* com.example.valueinsoftbackend.Service.openitems.ArOpenItemService.applyCreditNote(..))")
    public Object arNote(ProceedingJoinPoint joinPoint) throws Throwable {
        return observe(joinPoint, "AR", "credit_note");
    }

    @Around("execution(* com.example.valueinsoftbackend.Service.openitems.ApOpenItemService.allocateReceipt(..))")
    public Object apReceipt(ProceedingJoinPoint joinPoint) throws Throwable {
        return observe(joinPoint, "AP", "receipt");
    }

    @Around("execution(* com.example.valueinsoftbackend.Service.openitems.ApOpenItemService.applyDebitNote(..))")
    public Object apNote(ProceedingJoinPoint joinPoint) throws Throwable {
        return observe(joinPoint, "AP", "debit_note");
    }

    private Object observe(ProceedingJoinPoint joinPoint, String side, String source) throws Throwable {
        Timer.Sample sample = metrics.startAllocation();
        String outcome = "success";
        try {
            Object result = joinPoint.proceed();
            if (result instanceof OpenItemsWriteModels.AllocationResult allocation
                    && allocation.idempotencyReplay()) {
                outcome = "replay";
                metrics.recordIdempotencyReplay(side, source);
            }
            return result;
        } catch (DataIntegrityViolationException exception) {
            outcome = "trigger_rejection";
            metrics.recordTriggerRejection(side, source);
            throw exception;
        } catch (RuntimeException exception) {
            outcome = "rejected";
            throw exception;
        } finally {
            metrics.finishAllocation(sample, side, source, outcome);
        }
    }
}
