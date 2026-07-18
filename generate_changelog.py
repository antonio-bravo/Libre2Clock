import os
from git import Repo, NULL_TREE

def generate_changelog(repo_path, output_file):
    repo = Repo(repo_path)
    commits = list(repo.iter_commits('main', max_count=50))  # Obtiene los últimos 50 commits

    # Eliminar el archivo de changelog si ya existe
    if os.path.exists(output_file):
        os.remove(output_file)
    
    try:
        # Abrir el archivo en modo 'write' para escribir todo el contenido de una vez
        with open(output_file, 'w') as f:
            f.write("# Changelog\n\n")  # Cabecera del changelog

            # Iterar sobre todos los commits en orden descendente (del más reciente al más antiguo)
            for commit in commits:
                commit_hash = commit.hexsha[:7]
                f.write(f"## {commit_hash} ({commit.committed_datetime.strftime('%b %d, %Y %H:%M:%S')})\n")
                f.write(f"{commit.message.strip()} — {commit.author.name}\n")
                
                # Enlace para detalles que te lleva a la sección correspondiente
                f.write(f"[detail](#{commit_hash}-details)\n\n")

                # Obtener las diferencias entre este commit y su padre
                diffs = commit.diff(commit.parents[0] if commit.parents else NULL_TREE)
                if diffs:
                    # Sección anclada con ID para los detalles de los archivos modificados
                    f.write(f"<details id='{commit_hash}-details'>\n")
                    f.write("<summary>Changed files</summary>\n\n")
                    for diff in diffs:
                        change_type = ""
                        if diff.new_file:  # Archivo añadido
                            change_type = "Added"
                        elif diff.deleted_file:  # Archivo borrado
                            change_type = "Deleted"
                        elif diff.renamed_file:  # Archivo renombrado
                            change_type = f"Renamed from {diff.a_path} to {diff.b_path}"
                        else:  # Archivo modificado
                            change_type = "Modified"
                        
                        # Mostrar la ruta del archivo y el tipo de cambio
                        f.write(f"- {diff.b_path or diff.a_path} [{change_type}]\n")
                    
                    f.write("</details>\n\n")
                else:
                    f.write(f"No files changed in this commit.\n\n")
                
                f.write("\n---\n")  # Separador entre commits
    except Exception as e:
        print(f"Error al generar el changelog: {e}")

if __name__ == "__main__":
    repo_path = '.'  # Usa el directorio actual como el repositorio
    output_file = os.path.join(repo_path, 'CHANGELOG.md')
    generate_changelog(repo_path, output_file)