package com.jllado.weightcontrol.domain;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "stretching_sets", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "normalized_name"}))
@Getter
@Setter
public class StretchingSet {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(nullable = false, length = 255)
    private String name;
    @Column(name = "normalized_name", nullable = false, length = 765)
    private String normalizedName;
    @OneToMany(mappedBy = "stretchingSet", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<StretchingSetEntry> entries = new ArrayList<>();
}
