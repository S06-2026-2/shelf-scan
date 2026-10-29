package br.com.virabrequim.database_service.dto;

import br.com.virabrequim.database_service.domain.node.BibliotecaNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record NovaBiblioteca(
        @NotBlank @Size(max = 100) String nome,
        @NotBlank @Email String email,
        @NotNull @Valid Endereco endereco,
        @Pattern(regexp = "\\d{2}:\\d{2}") String horarioAbertura,
        @Pattern(regexp = "\\d{2}:\\d{2}") String horarioFechamento
) {

    // Só copia os dados (mesmo padrão do NovoLivro.paraLivro). As regras de nome, email, estante padrão e horário
    // são aplicadas pelo próprio BibliotecaNode; a unicidade do email fica com o service
    public BibliotecaNode paraBiblioteca() {
        BibliotecaNode biblioteca = new BibliotecaNode(nome, email);
        biblioteca.setLogradouro(endereco.logradouro());
        biblioteca.setNumero(endereco.numero());
        biblioteca.setCep(endereco.cep());
        biblioteca.setCidade(endereco.paraCidade());
        biblioteca.setHorarioAbertura(horarioAbertura);
        biblioteca.setHorarioFechamento(horarioFechamento);
        return biblioteca;
    }
}
