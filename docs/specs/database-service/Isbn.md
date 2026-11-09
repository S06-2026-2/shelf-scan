classe: br.com.virabrequim.database_service.agente.Isbn

# Spec: Isbn

## Contexto

Validação de ISBN pelo **dígito verificador**, usada pelo agente enriquecedor como trava contra o ISBN mais fácil de
alucinar: um número com o formato certo, mas inexistente. Não garante que o ISBN é do livro certo, só que é um ISBN
bem formado.

- **ISBN-13:** 13 dígitos; multiplicando os dígitos por pesos alternados 1, 3, 1, 3..., a soma é múltipla de 10.
- **ISBN-10:** 9 dígitos + 1 dígito ou `X` (vale 10, só na última posição); multiplicando por pesos 10, 9, ..., 1, a
  soma é múltipla de 11.

Classe utilitária (sem estado, construtor privado).

## API esperada

| Assinatura | Para quê | Regras |
| --- | --- | --- |
| `static String normalizado(String isbn)` | ISBN normalizado ou `null` se inválido | I1–I4 |

## Regras

- **I1.** `null` retorna `null`.
- **I2.** Antes de validar, remove espaços e hífens e passa para maiúsculas (`x` vira `X`).
- **I3.** Retorna o texto normalizado (I2) se for um ISBN-13 válido (13 dígitos com dígito verificador certo) ou um
  ISBN-10 válido (9 dígitos + dígito ou `X`, com dígito verificador certo).
- **I4.** Qualquer outra coisa retorna `null`: dígito verificador errado, tamanho diferente de 10 ou 13, letras, `X`
  fora da última posição do ISBN-10, `X` no ISBN-13, texto vazio.

## Exemplos

- **E1 (I2, I3).** `normalizado("978-85-359-1484-9")` = "9788535914849".
- **E2 (I3).** `normalizado("9788535914849")` = "9788535914849".
- **E3 (I2, I3).** `normalizado("85 359 1484 6")` = "8535914846" (ISBN-10).
- **E4 (I2, I3).** `normalizado("0-8044-2957-x")` = "080442957X" (ISBN-10 com `X`).
- **E5 (I4).** `normalizado("9788535914848")` = `null` (dígito verificador errado no ISBN-13).
- **E6 (I4).** `normalizado("8535914844")` = `null` (dígito verificador errado no ISBN-10).
- **E7 (I4).** `normalizado("123")`, `normalizado("")` e `normalizado("abcdefghij")` = `null`.
- **E8 (I4).** `normalizado("978853591484X")` = `null` (`X` não vale no ISBN-13).
- **E9 (I1).** `normalizado(null)` = `null`.

## Casos de borda

- **Separadores misturados** ("978 85-359 1484-9") também são aceitos (I2).
- **Outros separadores** (ponto, barra) não são removidos: o ISBN fica inválido (I4).

## Fora do escopo

- Conferir se o ISBN existe ou é do livro (precisaria consultar uma base externa).
- Converter ISBN-10 em ISBN-13.
