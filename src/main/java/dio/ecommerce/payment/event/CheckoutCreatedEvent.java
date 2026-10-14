package dio.ecommerce.payment.event;

public record CheckoutCreatedEvent(String checkoutCode, String status) {}