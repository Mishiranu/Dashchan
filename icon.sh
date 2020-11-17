#!/bin/bash -e

cp 'icon.svg' 'icon-foreground.svg'
cp 'icon.svg' 'icon-logo.svg'
inkscape --batch-process 'icon-foreground.svg' --actions $(printf '%s;' \
	'select:dash-group' 'ObjectUnSetClipPath' 'EditDeselect' \
	'select:circle' 'select:dash-clip-path' 'EditDelete' 'EditDeselect' \
	'FileSave' 'FileQuit')
sed -e 's/style=".*"/style="fill:#ffffff"/' -i 'icon-logo.svg'
inkscape --batch-process 'icon-logo.svg' --actions $(printf '%s;' \
	'select:dash-drop' 'EditDelete' 'EditDeselect' \
	'select:dash-group' 'SelectionUnGroup' 'EditDeselect' \
	'EditSelectAll' 'SelectionDiff' 'EditDeselect' \
	'FileSave' 'FileQuit')

dimensions=(mdpi:1 hdpi:1.5 xhdpi:2 xxhdpi:3 xxxhdpi:4)
for dimension in ${dimensions[@]}; do
	resource="${dimension%:*}"
	scale="${dimension#*:}"
	mkdir -p "res/mipmap-$resource" "res/drawable-$resource"
	out_launcher="res/mipmap-$resource/ic_launcher.png"
	out_foreground="res/drawable-$resource/ic_launcher_foreground.png"
	out_notification="res/drawable-$resource/ic_notification.png"
	out_logo="res/mipmap-$resource/ic_logo.png"
	out=("$out_launcher" "$out_foreground" "$out_logo")
	size="$(bc <<< "48 * $scale / 1")"
	inkscape 'icon.svg' --export-area='15:15:93:93' -w "$size" -h "$size" -o "$out_launcher"
	inkscape 'icon-logo.svg' --export-area='15:15:93:93' -w "$size" -h "$size" -o "$out_logo"
	size="$(bc <<< "108 * $scale / 1")"
	inkscape 'icon-foreground.svg' -w "$size" -h "$size" -o "$out_foreground"
	size="$(bc <<< "24 * $scale / 1")"
	inkscape 'icon-logo.svg' --export-area='15:15:93:93' -w "$size" -h "$size" -o "$out_notification"
	optipng "${out[@]}"
	exiftool -all= -overwrite_original "${out[@]}"
done
rm -f 'icon-foreground.svg' 'icon-logo.svg'
