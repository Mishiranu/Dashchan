#!/bin/bash -e

cp 'icon.svg' 'icon-foreground.svg'
cp 'icon.svg' 'icon-logo.svg'
inkscape --select 'dash-group' --verb 'ObjectUnSetClipPath' --verb 'EditDeselect' \
	--select 'circle' --select 'dash-clip-path' --verb 'EditDelete' --verb 'EditDeselect' \
	--verb='FileSave' --verb 'FileQuit' 'icon-foreground.svg'
sed -e 's/style=".*"/style="fill:#ffffff"/' -i 'icon-logo.svg'
inkscape --select 'dash-drop' --verb 'EditDelete' --verb 'EditDeselect' \
	--select 'dash-group' --verb 'SelectionUnGroup' --verb 'EditDeselect' \
	--verb 'EditSelectAll' --verb 'SelectionDiff' --verb 'EditDeselect' \
	--verb='FileSave' --verb 'FileQuit' 'icon-logo.svg'

dimensions=(mdpi:1 hdpi:1.5 xhdpi:2 xxhdpi:3 xxxhdpi:4)
for dimension in ${dimensions[@]}; do
	resource="${dimension%:*}"
	scale="${dimension#*:}"
	mkdir -p "res/mipmap-$resource" "res/drawable-$resource"
	out_launcher="res/mipmap-$resource/ic_launcher.png"
	out_foreground="res/drawable-$resource/ic_launcher_foreground.png"
	out_logo="res/mipmap-$resource/ic_logo.png"
	out=("$out_launcher" "$out_foreground" "$out_logo")
	size="`bc <<< "48 * $scale"`"
	inkscape 'icon.svg' -a 15:15:93:93 -w "$size" -h "$size" -e "$out_launcher"
	inkscape 'icon-logo.svg' -a 15:15:93:93 -w "$size" -h "$size" -e "$out_logo"
	size="`bc <<< "108 * $scale"`"
	inkscape 'icon-foreground.svg' -w "$size" -h "$size" -e "$out_foreground"
	optipng "${out[@]}"
	exiftool -all= -overwrite_original "${out[@]}"
done
rm -f 'icon-foreground.svg' 'icon-logo.svg'
