package com.hawkify.config;

import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConverterFactory;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${hawkify.uploads.dir:uploads}")
    private String directorioUploads;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String ubicacion = Path.of(directorioUploads).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(ubicacion.endsWith("/") ? ubicacion : ubicacion + "/")
                .setCachePeriod(60 * 60 * 24 * 30);
    }

    /** Los filtros llegan en minusculas (?estado=en_curso), igual que en el JSON. */
    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverterFactory(new EnumSinMayusculas());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static final class EnumSinMayusculas implements ConverterFactory<String, Enum> {
        @Override
        public <T extends Enum> Converter<String, T> getConverter(Class<T> tipo) {
            return valor -> valor == null || valor.isBlank()
                    ? null
                    : (T) Enum.valueOf(tipo, valor.trim().toUpperCase());
        }
    }
}
