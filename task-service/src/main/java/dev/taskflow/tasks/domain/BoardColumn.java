package dev.taskflow.tasks.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "board_columns")
@Getter
@Setter
public class BoardColumn {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "board_id", nullable = false, updatable = false)
    private Board board;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(nullable = false)
    private int position;

    @Column(name = "is_terminal", nullable = false)
    private boolean terminal;

    public static BoardColumn create(Board board, String name, int position, boolean terminal) {
        BoardColumn column = new BoardColumn();
        column.id = UUID.randomUUID();
        column.board = board;
        column.name = name;
        column.position = position;
        column.terminal = terminal;
        return column;
    }
}
