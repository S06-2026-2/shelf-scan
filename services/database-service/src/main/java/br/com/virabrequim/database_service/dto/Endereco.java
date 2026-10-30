package br.com.virabrequim.database_service.dto;

import br.com.virabrequim.database_service.domain.node.CidadeNode;
import br.com.virabrequim.database_service.domain.node.EstadoNode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record Endereco(
        @NotBlank @Size(max = 150) String logradouro,
        @Size(max = 10) String numero,
        @Pattern(regexp = "\\d{5}-?\\d{3}") String cep,
        @NotNull @Min(1_000_000) @Max(9_999_999) Integer cidadeCodigoIbge,
        @NotBlank @Size(max = 100) String cidadeNome,
        @NotBlank @Pattern(regexp = "[A-Za-z]{2}") String estadoSigla
) {

    // Só monta os nós; quem decide reaproveitar a cidade/estado que já existem no banco é o service
    public CidadeNode paraCidade() {
        EstadoNode estado = new EstadoNode(CidadeNode.codigoDoEstado(cidadeCodigoIbge), estadoSigla);
        return new CidadeNode(cidadeCodigoIbge, cidadeNome, estado);
    }
}
