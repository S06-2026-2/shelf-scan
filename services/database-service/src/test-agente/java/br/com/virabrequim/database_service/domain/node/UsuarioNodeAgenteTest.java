package br.com.virabrequim.database_service.domain.node;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UsuarioNodeAgenteTest {

    @Nested
    @DisplayName("Criação")
    class Criacao {

        @Test
        @DisplayName("E1 (UN2, UN3, UN4): cria usuário com nome e email normalizados e estante padrão")
        void criaUsuario() {
            UsuarioNode usuario = new UsuarioNode("  Ana Souza ", "  Ana@Email.COM ");
            assertAll(
                    () -> assertEquals("Ana Souza", usuario.getNome()),
                    () -> assertEquals("ana@email.com", usuario.getEmail()),
                    () -> assertThat(usuario.getEstantes()).hasSize(1),
                    () -> assertEquals("Minha estante", usuario.getEstantes().get(0).getNome()),
                    () -> assertSame(usuario.getEstantes().get(0), usuario.getEstantePadrao()));
        }

        @ParameterizedTest(name = "E2: nome [{0}] lança IllegalArgumentException")
        @DisplayName("E2 (UN2): nome inválido no construtor")
        @CsvSource(value = {"NULL", "''", "'   '"}, nullValues = "NULL")
        void nomeInvalido(String nome) {
            assertThrows(IllegalArgumentException.class, () -> new UsuarioNode(nome, "ana@email.com"));
        }

        @ParameterizedTest(name = "E3: email [{0}] lança IllegalArgumentException")
        @DisplayName("E3 (UN3): email inválido no construtor")
        @CsvSource(value = {"NULL", "''", "'   '"}, nullValues = "NULL")
        void emailInvalido(String email) {
            assertThrows(IllegalArgumentException.class, () -> new UsuarioNode("Ana", email));
        }

        @Test
        @DisplayName("E7 (UN1): construtor vazio tem estantes vazia (não null) e estantePadrao null")
        void construtorVazio() {
            UsuarioNode usuario = new UsuarioNode();
            assertAll(
                    () -> assertNotNull(usuario.getEstantes()),
                    () -> assertThat(usuario.getEstantes()).isEmpty(),
                    () -> assertNull(usuario.getEstantePadrao()));
        }

        @Test
        @DisplayName("E8 (UN4): dois usuários têm estantes padrão diferentes")
        void estantesPadraoDiferentes() {
            UsuarioNode ana = new UsuarioNode("Ana", "ana@email.com");
            UsuarioNode bruno = new UsuarioNode("Bruno", "bruno@email.com");
            assertNotSame(ana.getEstantePadrao(), bruno.getEstantePadrao());
        }

        @Test
        @DisplayName("Caso de borda (UN2): maiúsculas no nome são mantidas")
        void maiusculasNoNome() {
            assertEquals("ANA Souza", new UsuarioNode("ANA Souza", "ana@email.com").getNome());
        }

        @Test
        @DisplayName("UN5 (derivado): nome e email inválidos lançam a exceção do nome")
        @Tag("derivado")
        void nomeValidadoAntesDoEmail() {
            IllegalArgumentException doNome = assertThrows(IllegalArgumentException.class,
                    () -> new UsuarioNode(null, "ana@email.com"));
            IllegalArgumentException dosDois = assertThrows(IllegalArgumentException.class,
                    () -> new UsuarioNode(null, null));
            assertEquals(doNome.getMessage(), dosDois.getMessage());
        }

        @Test
        @DisplayName("UN4 (derivado): NOME_ESTANTE_PADRAO é \"Minha estante\"")
        @Tag("derivado")
        void constanteNomeEstantePadrao() {
            assertEquals("Minha estante", UsuarioNode.NOME_ESTANTE_PADRAO);
        }
    }

    @Nested
    @DisplayName("Setters")
    class Setters {

        @Test
        @DisplayName("E4 (UN2): setNome remove espaços; nome em branco lança exceção e mantém o anterior")
        void setNome() {
            UsuarioNode usuario = new UsuarioNode("Ana", "ana@email.com");
            usuario.setNome("  Ana Lima ");
            assertEquals("Ana Lima", usuario.getNome());

            assertThrows(IllegalArgumentException.class, () -> usuario.setNome("  "));
            assertEquals("Ana Lima", usuario.getNome());
        }

        @Test
        @DisplayName("E5 (UN3): setEmail normaliza; null lança exceção e mantém o anterior")
        void setEmail() {
            UsuarioNode usuario = new UsuarioNode("Ana", "ana@email.com");
            usuario.setEmail(" NOVO@Email.com ");
            assertEquals("novo@email.com", usuario.getEmail());

            assertThrows(IllegalArgumentException.class, () -> usuario.setEmail(null));
            assertEquals("novo@email.com", usuario.getEmail());
        }

        @Test
        @DisplayName("UN2 (derivado): setNome(null) lança exceção e mantém o nome")
        @Tag("derivado")
        void setNomeNull() {
            UsuarioNode usuario = new UsuarioNode("Ana", "ana@email.com");
            assertThrows(IllegalArgumentException.class, () -> usuario.setNome(null));
            assertEquals("Ana", usuario.getNome());
        }

        @Test
        @DisplayName("UN3 (derivado): setEmail em branco lança exceção e mantém o email")
        @Tag("derivado")
        void setEmailEmBranco() {
            UsuarioNode usuario = new UsuarioNode("Ana", "ana@email.com");
            assertThrows(IllegalArgumentException.class, () -> usuario.setEmail("   "));
            assertEquals("ana@email.com", usuario.getEmail());
        }
    }

    @Nested
    @DisplayName("normalizarEmail")
    class NormalizarEmail {

        @Test
        @DisplayName("E6 (UN3): normalizarEmail remove espaços e converte para minúsculas")
        void normaliza() {
            assertEquals("ana@email.com", UsuarioNode.normalizarEmail("  Ana@Email.COM "));
        }

        @ParameterizedTest(name = "E6: normalizarEmail([{0}]) lança IllegalArgumentException")
        @DisplayName("E6 (UN3): normalizarEmail com email inválido")
        @CsvSource(value = {"NULL", "'  '"}, nullValues = "NULL")
        void invalido(String email) {
            assertThrows(IllegalArgumentException.class, () -> UsuarioNode.normalizarEmail(email));
        }

        @Test
        @DisplayName("UN3 (derivado): normalizarEmail(\"\") lança IllegalArgumentException")
        @Tag("derivado")
        void vazio() {
            assertThrows(IllegalArgumentException.class, () -> UsuarioNode.normalizarEmail(""));
        }
    }
}
