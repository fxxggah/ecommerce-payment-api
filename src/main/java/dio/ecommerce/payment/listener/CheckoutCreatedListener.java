package dio.ecommerce.payment.listener;

import dio.ecommerce.payment.entity.Payment;
import dio.ecommerce.payment.event.CheckoutCreatedEvent;
import dio.ecommerce.payment.event.PaymentCreatedEvent;
import dio.ecommerce.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Function;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class CheckoutCreatedListener {

    private final PaymentService paymentService;

    @Bean
    public Function<CheckoutCreatedEvent, PaymentCreatedEvent> checkoutCreated() {
        return event -> {
            log.info("checkoutCreatedEvent={}", event);
            final Payment payment = paymentService.create(event).orElseThrow();
            return new PaymentCreatedEvent(payment.getCheckoutCode(), payment.getCode());
        };
    }
}