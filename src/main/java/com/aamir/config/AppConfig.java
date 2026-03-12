package com.aamir.config;

import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@EnableJpaAuditing
public class AppConfig {

    @Bean
    public ModelMapper modelMapper() {
        ModelMapper mapper = new ModelMapper();

        // STRICT prevents accidental field mismatches (e.g., id → productId)
        mapper.getConfiguration()
                .setMatchingStrategy(MatchingStrategies.STRICT);

        return mapper;
    }
}
