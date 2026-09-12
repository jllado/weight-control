package com.jllado.weightcontrol.domain;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "stretching_set_entries")
@Getter
@Setter
public class StretchingSetEntry {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "stretching_set_id", nullable = false)
    private StretchingSet stretchingSet;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "exercise_id", nullable = false)
    private Exercise exercise;
    @Column(nullable = false)
    private Integer position;

    @Enumerated(EnumType.STRING)
    @Column(name = "stretching_unit", nullable = false)
    private StretchingUnit stretchingUnit = StretchingUnit.SECONDS;
    @ElementCollection
    @CollectionTable(name = "stretching_set_holds", joinColumns = @JoinColumn(name = "entry_id"))
    @OrderColumn(name = "position")
    @Column(name = "duration_seconds", nullable = false)
    private List<Integer> durations = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "stretching_set_breaths", joinColumns = @JoinColumn(name = "entry_id"))
    @OrderColumn(name = "position")
    @Column(name = "breaths", nullable = false)
    private List<Integer> breaths = new ArrayList<>();
}
