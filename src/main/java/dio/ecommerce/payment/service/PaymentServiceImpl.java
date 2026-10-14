package dio.ecommerce.payment.service;

import dio.ecommerce.payment.event.CheckoutCreatedEvent;
import dio.ecommerce.payment.entity.Payment;
import dio.ecommerce.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;

    @Override
    public Optional<Payment> create(CheckoutCreatedEvent checkoutCreatedEvent) {

        final Payment payment = Payment.builder()
                .checkoutCode(checkoutCreatedEvent.checkoutCode())
                .code(UUID.randomUUID().toString())
                .build();

        return Optional.of(paymentRepository.save(payment));
    }
}
