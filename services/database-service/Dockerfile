FROM eclipse-temurin:25-jdk-alpine AS builder

# Define o diretório de trabalho dentro do container
WORKDIR /app

# Copia os arquivos de configuração do Maven primeiro para otimizar o cache do Docker
COPY pom.xml mvnw ./
COPY .mvn .mvn

# Dá permissão de execução ao wrapper do Maven
RUN chmod +x mvnw

# Baixa as dependências do Maven (aproveita o cache se o pom.xml não mudar)
RUN ./mvnw dependency:go-offline -B

# Copia o código-fonte do microsserviço
COPY src ./src

# Compila o projeto gerando o arquivo .jar ignorando testes (obs.: testes já rodam no GitHub Actions)
RUN ./mvnw clean package -DskipTests

'

FROM eclipse-temurin:25-jre-alpine

# Cria um usuário não-root por segurança
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# Copia apenas o .jar gerado na Etapa 1 (builder)
COPY --from=builder /app/target/*.jar app.jar

# Define as permissões para o usuário não-root
RUN chown -R appuser:appgroup /app
USER appuser

# Expõe a porta padrão Spring Boot
EXPOSE 8080

# Comando para rodar a aplicação Java
ENTRYPOINT ["java", "-jar", "app.jar"]
