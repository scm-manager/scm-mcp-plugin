# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## 1.3.2 - 2026-08-20
### Fixed
- Update of MCP library to fix CVE-2026-34237 and CVE-2026-35568

## 1.3.1 - 2026-08-18
### Fixed
- HTTP status codes in responses for MCP requests

## 1.3.0 - 2026-07-14
### Added
- Tool to create frontend links for different types of entities (e.g. repositories, files, directories, branches and tags)

## 1.2.1 - 2026-06-05
### Fixed
- Return overall number of lines if file is truncated

## 1.2.0 - 2026-02-25
### Added
- Extension point for diff tool

### Changed
- Enhanced diff tool with filtering, limits, comparison between branches, and option to ignore whitespaces

## 1.1.2 - 2026-01-26
### Fixed
- Handle unauthorized exceptions to prevent internal server errors

## 1.1.1 - 2026-01-21
### Fixed
- NullPointerException in cases, where an SCM MCP tool returns no structured content

## 1.1.0 - 2026-01-20
### Changed
- Simplified result object for repositories without HAL links
- Details for repositories are optional
- Interface for extension point improved for usage in plugins

## 1.0.1 - 2025-09-29
### Fixed
- Usage of default values if omitted

## 1.0.0 - 2025-09-29
### Added
- Initial implementation of a MCP Server for the SCM-Manager

