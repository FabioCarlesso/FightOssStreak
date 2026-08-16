package dev.fos.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

/** Chave composta (usuário, nó), usada por progresso e agenda de SRS. */
@Embeddable
public class UserNodeKey implements Serializable {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "node_id", nullable = false)
    private Long nodeId;

    protected UserNodeKey() {
        // JPA
    }

    public UserNodeKey(Long userId, Long nodeId) {
        this.userId = userId;
        this.nodeId = nodeId;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getNodeId() {
        return nodeId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof UserNodeKey key
                && Objects.equals(userId, key.userId)
                && Objects.equals(nodeId, key.nodeId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, nodeId);
    }
}
