package uz.bizcontrol.config;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.datatype.hibernate6.Hibernate6Module;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the Jackson Hibernate6 module so JPA entities with lazy associations
 * can be serialized to JSON safely.
 *
 * <p>Without this, returning an entity whose {@code @ManyToOne(LAZY)} field is an
 * uninitialized Hibernate proxy makes Jackson throw
 * "No serializer found for ByteBuddyInterceptor" → HTTP 500 on list endpoints.</p>
 *
 * <p>FORCE_LAZY_LOADING is intentionally left <b>disabled</b>: uninitialized lazy
 * associations serialize as {@code null} (no N+1 storms, no infinite recursion on
 * bidirectional relations). Associations that must appear in list responses are
 * marked EAGER on their entities instead.</p>
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Module hibernate6Module() {
        Hibernate6Module module = new Hibernate6Module();
        module.disable(Hibernate6Module.Feature.FORCE_LAZY_LOADING);
        module.enable(Hibernate6Module.Feature.SERIALIZE_IDENTIFIER_FOR_LAZY_NOT_LOADED_OBJECTS);
        return module;
    }
}
