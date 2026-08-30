package com.kologeski.interviewassistant.auth;

public interface IdentityDelivery {
    DeliveryReceipt deliver(IdentityToken.Purpose purpose, String email, String rawToken);
    record DeliveryReceipt(String status, String developmentPath) { }
}
