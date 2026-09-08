from pypdf import PdfReader
reader = PdfReader('/tmp/br05_commands.pdf')
text = ''
for page in reader.pages:
    text += page.extract_text() + '\n'
print(text)
