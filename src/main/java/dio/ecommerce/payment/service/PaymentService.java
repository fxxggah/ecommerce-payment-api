package dio.ecommerce.payment.service;

import dio.ecommerce.payment.entity.Payment;
import dio.ecommerce.payment.event.CheckoutCreatedEvent;

import java.util.Optional;

public interface PaymentService {

    Optional<Payment> create(CheckoutCreatedEvent checkoutCreatedEvent);

}
