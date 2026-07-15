package com.myworktea.domain;

import jakarta.persistence.*;

/** An industry/category tag (IT, Technology, Marketing, Finance, ...). */
@Entity
@Table(name = "categories", indexes = @Index(name = "idx_category_name", columnList = "name", unique = true))
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 60)
    private String name;

    @Column(length = 80)
    private String slug;

    public Category() { }

    public Category(String name, String slug) {
        this.name = name;
        this.slug = slug;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
}