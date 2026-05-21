package com.nyasha.store.entities;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "roles", uniqueConstraints = {
        @UniqueConstraint(name = "uq_role_name", columnNames = "name")
})
@Data
public class Role {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long roleId;

    private String name;

    public Role() {
    }

    public Role(String name) {
        this.name = name;
    }
}
