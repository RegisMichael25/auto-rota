# Fase de Planejamento

### O que estamos contruindo?
    Estamos contruindo um sistema de rotas para lojistas. O sistema deve ser capaz de identificar melhores rotas de entrega para um caminhoneiro onde os pontos de entrega sejam geograficamente mais proximos. O sistema deve identificar que ja foi feita a entrega sozinho ou com a confirmacao do usuario. As entregas devem ser feitas somente no brasil.
    
    A dor que queremos resolver, engloba todos os lojistas que entregam ou distribuem produtos entre clientes diversos. Com ele, o usuario pode ter uma base de quanto vai gastar de gasolina nesse percurso, saber qual a melhor rota para aquele entregador pensando em economia e ter o tempo de entrega controlado. Tras beneficos certos para o fluxo de entrega.

### O que exatamente sera entregue?

    Sera entregue um sistema simples que identifica o ponto inicial(local onde o logista possui a unidade fisica e onde os entregadores devem sair) e os pontos de entrega que seriam os clientes que desejam receber seus produtos. 

### Vale a apena faze-lo?

    Sim! Vale a pena para que os nossos lojistas parceitos tenham mais controle dos seus gastos, funcionarios, tempo e valores.
    
### O que nao sera feito? 

    Nao sera feito autenticacao.
    Nao sera feito nenhum tipo de cadastro de cliente
    Nao sera feito nenhum cadastro de distribuidora
    Nao deve calcular combustivel(por enquanto)

### O que sera feito?

    Sera feito verificacao geografica

### Riscos?

    Rotas de distâncias fora do alcance do lojista
    Qualquer pessoa com acesso a URL poder acessar o sistema
    Perigos de RLS

### O que o sistema precisa fazer? 

    O sistema deve permitir adicionar o local da unidade e ate 10 locais de entrega.
    O sistema deve criar um tracejado no mapa mostrando a rota possivel.
    O sistema deve utilizar a api viacep para identificar os ceps e sua posicao
    Manter histórico de entregas
