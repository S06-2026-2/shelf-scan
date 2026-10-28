package br.com.virabrequim.database_service.domain.node;

import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.data.neo4j.core.schema.Node;

// Biblioteca é um usuário: no grafo o nó recebe os labels Biblioteca e Usuario (herdado de UsuarioNode)
@Node("Biblioteca")
@ToString(callSuper = true)
@NoArgsConstructor
public class BibliotecaNode extends UsuarioNode {
}
