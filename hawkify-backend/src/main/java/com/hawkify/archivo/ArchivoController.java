package com.hawkify.archivo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.YearMonth;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.hawkify.common.ApiException;

/**
 * Subida de imagenes (fotos de herramientas y de perfil). Almacena en disco
 * local y sirve bajo /uploads/**. Para produccion se reemplaza por Supabase
 * Storage sin tocar el contrato: el frontend solo usa la URL devuelta.
 */
@RestController
public class ArchivoController {

    private static final Map<String, String> TIPOS = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp");

    private final Path raiz;

    public ArchivoController(@Value("${hawkify.uploads.dir:uploads}") String directorio) {
        this.raiz = Path.of(directorio).toAbsolutePath().normalize();
    }

    @PostMapping("/api/archivos")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> subir(@RequestParam("archivo") MultipartFile archivo) throws IOException {
        if (archivo.isEmpty()) {
            throw ApiException.invalido("El archivo está vacío");
        }
        String extension = TIPOS.get(archivo.getContentType());
        if (extension == null || !firmaValida(archivo, extension)) {
            throw ApiException.invalido("Solo se aceptan imágenes JPG, PNG o WEBP");
        }

        String carpeta = YearMonth.now().toString();
        Path destino = raiz.resolve(carpeta).resolve(UUID.randomUUID() + "." + extension).normalize();
        if (!destino.startsWith(raiz)) {
            throw ApiException.invalido("Ruta de archivo inválida");
        }
        Files.createDirectories(destino.getParent());
        try (InputStream in = archivo.getInputStream()) {
            Files.copy(in, destino, StandardCopyOption.REPLACE_EXISTING);
        }

        String url = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/uploads/").path(carpeta).path("/").path(destino.getFileName().toString())
                .toUriString();
        return Map.of("url", url);
    }

    /** Verifica los bytes magicos: el Content-Type lo controla el cliente y no basta. */
    private static boolean firmaValida(MultipartFile archivo, String extension) throws IOException {
        byte[] cabecera = new byte[12];
        try (InputStream in = archivo.getInputStream()) {
            if (in.read(cabecera) < 12) {
                return false;
            }
        }
        return switch (extension) {
            case "jpg" -> (cabecera[0] & 0xFF) == 0xFF && (cabecera[1] & 0xFF) == 0xD8;
            case "png" -> (cabecera[0] & 0xFF) == 0x89 && cabecera[1] == 'P' && cabecera[2] == 'N' && cabecera[3] == 'G';
            case "webp" -> cabecera[0] == 'R' && cabecera[1] == 'I' && cabecera[8] == 'W' && cabecera[9] == 'E';
            default -> false;
        };
    }
}
