package br.com.virabrequim.database_service.domain.node;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

import java.time.LocalDate;


@Node("Livro")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmprestimoNode {
    @Id
    @GeneratedValue
    private Long id;

    private LocalDate dataEmprestimo;
    private LocalDate dadaDevolucao;
}
