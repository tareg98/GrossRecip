package com.example.grossrecipes.data.dto

// This file used to hold one request/response class per REST action
// (Lists/create, Lists/set-checked, Lists/update-color, etc). The app moved
// to event sourcing - every action is now a generic EventEnvelope (see
// EventEnvelope.kt) instead of its own bespoke DTO, so nothing lives here
// anymore. Kept as an empty file rather than deleted so there's a record of
// where those old shapes used to be, in case old backend code still
// references the pre-event-sourcing routes.
