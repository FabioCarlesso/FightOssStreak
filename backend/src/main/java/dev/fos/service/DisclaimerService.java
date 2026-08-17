package dev.fos.service;

import dev.fos.config.FosProperties;
import dev.fos.model.DisclaimerAcceptance;
import dev.fos.repo.DisclaimerAcceptanceRepository;
import dev.fos.web.dto.ActivityDtos;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aceite do aviso de responsabilidade.
 *
 * <p>O aceite é sempre por versão de texto: aceitar a versão anterior não conta como aceite da
 * atual. Isso é o que faz "reexibir quando o texto mudar" funcionar de fato, em vez de ser uma
 * intenção escrita na doc.
 */
@Service
public class DisclaimerService {

    private final DisclaimerAcceptanceRepository repository;
    private final String currentVersion;

    public DisclaimerService(DisclaimerAcceptanceRepository repository, FosProperties properties) {
        this.repository = repository;
        this.currentVersion = properties.disclaimerVersion();
    }

    @Transactional(readOnly = true)
    public ActivityDtos.DisclaimerStatus status(Long userId) {
        return repository
                .findFirstByUserIdAndVersionOrderByAcceptedAtDesc(userId, currentVersion)
                .map(
                        acceptance ->
                                new ActivityDtos.DisclaimerStatus(
                                        true,
                                        acceptance.getVersion(),
                                        currentVersion,
                                        CurriculumQueryService.SHORT_SAFETY_NOTICE))
                .orElseGet(
                        () ->
                                new ActivityDtos.DisclaimerStatus(
                                        false,
                                        null,
                                        currentVersion,
                                        CurriculumQueryService.SHORT_SAFETY_NOTICE));
    }

    @Transactional
    public ActivityDtos.DisclaimerStatus accept(Long userId, String version) {
        if (!currentVersion.equals(version)) {
            throw new IllegalArgumentException(
                    "Aceite refere-se a uma versão desatualizada do aviso. Vigente: "
                            + currentVersion);
        }
        repository.save(new DisclaimerAcceptance(userId, version, Instant.now()));
        return status(userId);
    }

    public String currentVersion() {
        return currentVersion;
    }
}
