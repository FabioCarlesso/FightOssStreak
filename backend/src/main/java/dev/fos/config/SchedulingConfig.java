package dev.fos.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Liga o agendador, sem condição nenhuma.
 *
 * <p>Está em arquivo próprio para ser óbvio: até a #84 o app não tinha nada agendado, e a D38 havia
 * registrado que, se tivesse, o {@code @EnableScheduling} viria amarrado à credencial de envio de
 * e-mail — o que fazia sentido quando o único candidato era um resumo enviado por e-mail. A
 * manutenção da coleta de uso <b>não</b> pode herdar essa condição: ela agrega e expurga dado do
 * próprio banco, e um ambiente sem provedor de envio (dev, CI, instalação só com login social)
 * ficaria com a tabela crua crescendo para sempre e sem número nenhum agregado.
 *
 * <p>Consequência assumida: o job também roda em dev. É barato — sem evento, ele não faz nada.
 */
@Configuration
@EnableScheduling
class SchedulingConfig {}
