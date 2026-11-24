{{- define "tally.image" -}}
{{ .Values.image.repository }}@{{ required "image.digest is required: deploy by digest, make up sets it" .Values.image.digest }}
{{- end -}}

{{- define "tally.labels" -}}
app.kubernetes.io/name: tally
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}

{{- define "tally.securityContext" -}}
allowPrivilegeEscalation: false
readOnlyRootFilesystem: true
capabilities:
  drop: ["ALL"]
{{- end -}}

{{- define "tally.podSecurityContext" -}}
runAsNonRoot: true
runAsUser: 65532
runAsGroup: 65532
seccompProfile:
  type: RuntimeDefault
{{- end -}}
