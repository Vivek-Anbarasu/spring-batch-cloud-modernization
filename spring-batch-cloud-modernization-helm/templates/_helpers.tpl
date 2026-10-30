{{/*
Expand the name of the chart.
*/}}
{{- define "spring-batch-cloud-modernization.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
*/}}
{{- define "spring-batch-cloud-modernization.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Create chart name and version as used by the chart label.
*/}}
{{- define "spring-batch-cloud-modernization.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "spring-batch-cloud-modernization.labels" -}}
helm.sh/chart: {{ include "spring-batch-cloud-modernization.chart" . }}
{{ include "spring-batch-cloud-modernization.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Selector labels
*/}}
{{- define "spring-batch-cloud-modernization.selectorLabels" -}}
app.kubernetes.io/name: {{ include "spring-batch-cloud-modernization.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Create the name of the service account to use
*/}}
{{- define "spring-batch-cloud-modernization.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- default (include "spring-batch-cloud-modernization.fullname" .) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}

{{/*
Common annotations
*/}}
{{- define "spring-batch-cloud-modernization.annotations" -}}
meta.helm.sh/release-name: {{ .Release.Name }}
meta.helm.sh/release-namespace: {{ .Release.Namespace }}
{{- end }}

{{/*
Generate secret name for datasource
*/}}
{{- define "spring-batch-cloud-modernization.datasourceSecretName" -}}
{{- printf "%s-datasource" (include "spring-batch-cloud-modernization.fullname" .) }}
{{- end }}