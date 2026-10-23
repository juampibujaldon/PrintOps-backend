package com.printops.demo.entity;

import jakarta.persistence.*;

// Gasto extra de un presupuesto: un concepto libre con su importe (p. ej. "Rodamiento").
@Entity
@Table(name = "quote_extra_costs", indexes = @Index(name = "idx_quote_extra_costs_quote", columnList = "quote_id"))
public class QuoteExtraCost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quote_id", nullable = false)
    private Quote quote;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private Double amount;

    @Column(nullable = false)
    private Integer position = 0;

    public Long getId() { return id; }
    public Quote getQuote() { return quote; }
    public void setQuote(Quote quote) { this.quote = quote; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }
    public Integer getPosition() { return position; }
    public void setPosition(Integer position) { this.position = position; }
}
