# ADR-0001: On-device ML for category classification

## Status
Accepted

## Context
Need classify bank SMS into categories with no network analysis calls.

## Decision
Ship pre-trained on-device model for Egypt/Saudi banks; fine-tune locally on user corrections; ML deduces parsing rules; user can edit.

## Consequences
Offline capable; model size affects APK; cold-start accuracy depends on pre-trained coverage.
filePath">
/home/agents/SMSCostTracker/docs/adr/0001-on-device-ml-classification.md