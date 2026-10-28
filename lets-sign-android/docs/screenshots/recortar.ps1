# Remove a barra de status (topo) dos PNGs desta pasta. Chamado por capturar.sh.
# Uso: powershell -File recortar.ps1 -Pasta <pasta> -Topo <pixels>
param([string]$Pasta, [int]$Topo)
Add-Type -AssemblyName System.Drawing
Get-ChildItem -Path $Pasta -Filter *.png | ForEach-Object {
    $img = [System.Drawing.Image]::FromFile($_.FullName)
    $src = New-Object System.Drawing.Rectangle 0, $Topo, $img.Width, ($img.Height - $Topo)
    $bmp = New-Object System.Drawing.Bitmap $src.Width, $src.Height
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.DrawImage($img, (New-Object System.Drawing.Rectangle 0, 0, $src.Width, $src.Height), $src, [System.Drawing.GraphicsUnit]::Pixel)
    $g.Dispose(); $img.Dispose()
    $bmp.Save($_.FullName, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
}
