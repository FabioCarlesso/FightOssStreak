package dev.fos.web.dto;

import dev.fos.model.AccessStatus;
import dev.fos.model.Role;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/**
 * As contas do sistema, vistas por quem administra (#89, #90).
 *
 * <p>É a primeira resposta do app que carrega dado pessoal de <em>outras</em> pessoas — o e-mail de
 * todo mundo que se cadastrou. O que entra aqui está registrado em {@code docs/11-privacidade.md},
 * e o que fica de fora é decisão: não há senha, não há token, não há histórico de uso.
 */
public final class AdminUserDtos {

    private AdminUserDtos() {}

    /**
     * Uma conta na listagem.
     *
     * @param id id da conta. A tela precisa dele para agir, e não o exibe — o que a pessoa lê é
     *     e-mail e rótulo (#87)
     * @param label como a conta se chama: nome do provedor, nome do cadastro, ou o e-mail
     * @param email endereço da conta. É o {@code primary_email} quando já verificado; senão, o
     *     endereço que a identidade trouxe — cadastro ainda não confirmado tem e-mail e não tem
     *     dono do endereço, e esconder isso deixaria a linha sem como ser reconhecida
     * @param emailVerified se a conta é dona de um endereço verificado. É o que habilita a promoção
     *     a {@code ADMIN}
     * @param providers provedores vinculados ({@code google}, {@code senha}, …), na ordem em que
     *     foram criados
     * @param decidedAt quando o acesso foi decidido pela última vez
     * @param decidedReason por que foi bloqueada ou desbloqueada, quando quem decidiu escreveu
     */
    public record AdminUserView(
            Long id,
            String label,
            String email,
            boolean emailVerified,
            List<String> providers,
            Role role,
            AccessStatus accessStatus,
            Instant createdAt,
            Instant decidedAt,
            String decidedReason) {}

    /**
     * Uma página da listagem.
     *
     * @param page página pedida, base zero
     * @param size tamanho efetivo da página — pode ser menor que o pedido, porque há teto
     * @param total total de contas que casam com os filtros, não o tamanho desta página
     */
    public record AdminUserPage(
            List<AdminUserView> items, int page, int size, long total, int totalPages) {}

    /** Corpo de {@code POST /api/admin/usuarios/{id}/role}. */
    public record AdminRoleRequest(@NotNull Role role) {}

    /**
     * Corpo de {@code POST /api/admin/usuarios/{id}/status}.
     *
     * @param status {@code RECUSADO} bloqueia, {@code APROVADO} devolve o acesso
     * @param motivo por que — opcional, e guardado nas duas direções. Não é para a pessoa
     *     bloqueada: é para quem administrar o app daqui a seis meses e precisar entender a decisão
     */
    public record AdminStatusRequest(
            @NotNull AccessStatus status, @Size(max = 500) String motivo) {}
}
