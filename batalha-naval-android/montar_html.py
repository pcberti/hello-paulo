"""Monta o assets/index.html do app a partir do jogo em batalha-naval/index.html.

O arquivo do jogo é escrito para a página publicada (sem <!doctype>, <head> nem <body>,
que a publicação acrescenta). Aqui ele ganha esse cabeçalho e passa a usar as fontes
embutidas em assets/fonts, em vez de buscá-las no Google Fonts.

Uso: python3 montar_html.py <jogo.html> <saida.html>
"""
import re
import sys

origem, destino = sys.argv[1], sys.argv[2]
jogo = open(origem, encoding="utf-8").read()

sem_google = re.sub(r'<link rel="(?:preconnect|stylesheet)" href="https://fonts\.(?:googleapis|gstatic)\.com[^>]*>\n?', "", jogo)
if "fonts.googleapis.com" in sem_google:
    sys.exit("montar_html.py: sobrou link para o Google Fonts; atualize a expressão regular")

fim_estilo = sem_google.index("</style>") + len("</style>")
cabeca, corpo = sem_google[:fim_estilo], sem_google[fim_estilo:]

pagina = (
    "<!doctype html>\n"
    '<html lang="pt-BR">\n<head>\n'
    '<meta charset="utf-8">\n'
    '<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">\n'
    '<link rel="stylesheet" href="fontes.css">\n'
    + cabeca
    + "\n</head>\n<body>"
    + corpo
    + "</body>\n</html>\n"
)
open(destino, "w", encoding="utf-8").write(pagina)
