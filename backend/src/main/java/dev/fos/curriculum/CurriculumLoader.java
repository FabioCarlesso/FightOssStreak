package dev.fos.curriculum;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.fos.config.FosProperties;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/** Lê o índice de módulos e cada arquivo de módulo do classpath. */
@Component
public class CurriculumLoader {

    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;
    private final String indexLocation;

    public CurriculumLoader(
            ObjectMapper objectMapper, ResourceLoader resourceLoader, FosProperties properties) {
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
        this.indexLocation = properties.curriculum().index();
    }

    /** Carrega os módulos na ordem declarada no índice. */
    public List<CurriculumSource.Module> load() {
        CurriculumSource.Index index = read(indexLocation, CurriculumSource.Index.class);
        if (index.modules() == null || index.modules().isEmpty()) {
            throw new CurriculumException("Índice de currículo sem módulos: " + indexLocation);
        }

        String base = indexLocation.substring(0, indexLocation.lastIndexOf('/') + 1);
        List<CurriculumSource.ModuleRef> refs = new ArrayList<>(index.modules());
        refs.sort(Comparator.comparingInt(CurriculumSource.ModuleRef::order));

        List<CurriculumSource.Module> modules = new ArrayList<>(refs.size());
        for (CurriculumSource.ModuleRef ref : refs) {
            modules.add(read(base + ref.file(), CurriculumSource.Module.class));
        }
        return modules;
    }

    private <T> T read(String location, Class<T> type) {
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            throw new CurriculumException("Arquivo de currículo não encontrado: " + location);
        }
        try (InputStream in = resource.getInputStream()) {
            return objectMapper.readValue(in, type);
        } catch (IOException e) {
            throw new CurriculumException("Falha ao ler currículo em " + location + ": " + e.getMessage(), e);
        }
    }
}
