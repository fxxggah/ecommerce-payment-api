package dio.ecommerce.payment.event;

public record PaymentCreatedEvent(String checkoutCode, String paymentCode) {}