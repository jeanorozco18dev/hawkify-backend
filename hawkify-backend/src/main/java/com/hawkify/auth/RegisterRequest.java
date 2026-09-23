package com.hawkify.auth;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(

        @NotBlank(message = "El nombre completo es obligatorio")
        @Size(min = 3, max = 150, message = "El nombre completo debe tener entre 3 y 150 caracteres")
        String nombreCompleto,

        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "El correo no tiene un formato válido")
        @Size(max = 150, message = "El correo no puede superar 150 caracteres")
        String correo,

        @NotBlank(message = "La contraseña es obligatoria")
        @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "La contraseña debe combinar letras y números")
        String password,

        /** RF-01 / Ley 1581 de 2012: autorizacion previa, expresa e informada. */
        @NotNull(message = "Debes aceptar la Política de Tratamiento de Datos Personales")
        @AssertTrue(message = "Debes aceptar la Política de Tratamiento de Datos Personales")
        Boolean aceptaPolitica
) {
}
