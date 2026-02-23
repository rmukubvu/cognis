import React from 'react';
import { Badge } from './components/ui/badge';
import { Button } from './components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from './components/ui/card';
import { Input } from './components/ui/input';
import { Select } from './components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from './components/ui/table';

type DashboardSummary = {
  tasks_started: number;
  tasks_succeeded: number;
  tasks_failed: number;
  task_success_rate: number;
  p50_latency_ms: number;
  p95_latency_ms: number;
  average_cost_per_task_usd: number;
  failure_recovery_rate: number;
  safety_incident_rate: number;
  weekly_completed_tasks: number;
  active_users_7d: number;
  retention_7d: number;
  audit_events: number;
};

type AuditEvent = {
  id: string;
  timestamp: string;
  type: string;
  attributes: Record<string, unknown>;
};

const BASE_URL = import.meta.env.VITE_COGNIS_BASE_URL ?? 'http://127.0.0.1:8787';
const EVENT_LIMIT = 300;
type TimeFilter = '24h' | '7d' | '30d' | 'all';
type EventScope = 'all' | 'tool' | 'task';

export function App() {
  const [summary, setSummary] = React.useState<DashboardSummary | null>(null);
  const [events, setEvents] = React.useState<AuditEvent[]>([]);
  const [loading, setLoading] = React.useState(false);
  const [error, setError] = React.useState('');
  const [lastRefresh, setLastRefresh] = React.useState<Date | null>(null);
  const [typeFilter, setTypeFilter] = React.useState('all');
  const [scopeFilter, setScopeFilter] = React.useState<EventScope>('all');
  const [timeFilter, setTimeFilter] = React.useState<TimeFilter>('7d');
  const [searchFilter, setSearchFilter] = React.useState('');

  const refresh = React.useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const [summaryRes, eventsRes] = await Promise.all([
        fetch(`${BASE_URL}/dashboard/summary`),
        fetch(`${BASE_URL}/audit/events?limit=${EVENT_LIMIT}`),
      ]);
      if (!summaryRes.ok) throw new Error(`summary failed (${summaryRes.status})`);
      if (!eventsRes.ok) throw new Error(`events failed (${eventsRes.status})`);
      const summaryJson = (await summaryRes.json()) as DashboardSummary;
      const eventsJson = (await eventsRes.json()) as { events: AuditEvent[] };
      setSummary(summaryJson);
      setEvents(eventsJson.events ?? []);
      setLastRefresh(new Date());
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Unknown dashboard error');
    } finally {
      setLoading(false);
    }
  }, []);

  React.useEffect(() => {
    void refresh();
  }, [refresh]);

  const eventTypes = React.useMemo(() => ['all', ...Array.from(new Set(events.map((e) => e.type))).sort()], [events]);

  const filteredEvents = React.useMemo(() => {
    const now = Date.now();
    const timeWindow =
      timeFilter === '24h'
        ? 24 * 60 * 60 * 1000
        : timeFilter === '7d'
          ? 7 * 24 * 60 * 60 * 1000
          : timeFilter === '30d'
            ? 30 * 24 * 60 * 60 * 1000
            : null;
    return events.filter((event) => {
      if (scopeFilter === 'tool' && !event.type.startsWith('tool_')) return false;
      if (scopeFilter === 'task' && !event.type.startsWith('task_')) return false;
      if (typeFilter !== 'all' && event.type !== typeFilter) return false;
      if (timeWindow !== null) {
        const eventTs = new Date(event.timestamp).getTime();
        if (Number.isNaN(eventTs) || now - eventTs > timeWindow) return false;
      }
      if (!searchFilter) return true;
      const term = searchFilter.toLowerCase();
      return (
        event.type.toLowerCase().includes(term) ||
        JSON.stringify(event.attributes).toLowerCase().includes(term)
      );
    });
  }, [events, scopeFilter, searchFilter, timeFilter, typeFilter]);

  const exportCsv = React.useCallback(() => {
    const rows = filteredEvents.map((e) => ({
      id: e.id,
      timestamp: e.timestamp,
      type: e.type,
      attributes: JSON.stringify(e.attributes),
    }));
    const header = ['id', 'timestamp', 'type', 'attributes'];
    const data = [header, ...rows.map((r) => header.map((k) => toCsvCell(r[k as keyof typeof r])))]
      .map((line) => line.join(','))
      .join('\n');
    const blob = new Blob([data], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `cognis-audit-${Date.now()}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  }, [filteredEvents]);

  return (
    <div className="app-shell">
      <div className="orb orb-one" />
      <div className="orb orb-two" />
      <main className="container">
        <header className="page-header">
          <div>
            <h1 className="page-title">Cognis Operations Dashboard</h1>
            <p className="page-subtitle">Trusted autonomous operator with spend guardrails and accountable execution.</p>
          </div>
          <div className="actions">
            <div className="last-refresh">
              Last refresh: {lastRefresh ? lastRefresh.toLocaleTimeString() : 'never'}
            </div>
            <Button onClick={() => void refresh()} disabled={loading}>
              {loading ? 'Refreshing...' : 'Refresh'}
            </Button>
          </div>
        </header>

        {error ? <div className="banner-error">{error}</div> : null}

        {summary ? (
          <>
            <section className="kpi-grid">
              <MetricCard label="Task success rate" value={`${summary.task_success_rate.toFixed(1)}%`} />
              <MetricCard label="P95 latency" value={`${summary.p95_latency_ms.toFixed(0)} ms`} />
              <MetricCard label="Avg cost / task" value={`$${summary.average_cost_per_task_usd.toFixed(4)}`} />
              <MetricCard label="Safety incident rate" value={`${summary.safety_incident_rate.toFixed(2)}%`} />
            </section>

            <section className="split-grid">
              <Card>
                <CardHeader>
                  <CardTitle>Execution quality</CardTitle>
                  <CardDescription>Core reliability and recovery metrics.</CardDescription>
                </CardHeader>
                <CardContent className="stats-list">
                  <StatRow label="Tasks started" value={summary.tasks_started} />
                  <StatRow label="Tasks succeeded" value={summary.tasks_succeeded} />
                  <StatRow label="Tasks failed" value={summary.tasks_failed} />
                  <StatRow label="Failure recovery rate" value={`${summary.failure_recovery_rate.toFixed(1)}%`} />
                  <StatRow label="Retention (7d)" value={`${summary.retention_7d.toFixed(1)}%`} />
                </CardContent>
              </Card>

              <Card>
                <CardHeader>
                  <CardTitle>Execution funnel</CardTitle>
                  <CardDescription>Visual pass/fail distribution for current snapshot.</CardDescription>
                </CardHeader>
                <CardContent>
                  <FunnelBar
                    label="Started"
                    value={summary.tasks_started}
                    max={Math.max(summary.tasks_started, summary.tasks_succeeded, summary.tasks_failed, 1)}
                  />
                  <FunnelBar
                    label="Succeeded"
                    value={summary.tasks_succeeded}
                    max={Math.max(summary.tasks_started, summary.tasks_succeeded, summary.tasks_failed, 1)}
                  />
                  <FunnelBar
                    label="Failed"
                    value={summary.tasks_failed}
                    max={Math.max(summary.tasks_started, summary.tasks_succeeded, summary.tasks_failed, 1)}
                  />
                  <FunnelBar label="Weekly completed" value={summary.weekly_completed_tasks} max={Math.max(summary.weekly_completed_tasks, 1)} />
                </CardContent>
              </Card>
            </section>
          </>
        ) : null}

        <Card className="audit-card">
          <CardHeader>
            <CardTitle>Audit trail</CardTitle>
            <CardDescription>Filter by type, timeframe, or payload text. Export for compliance review.</CardDescription>
            <div className="filters">
              <Select value={scopeFilter} onChange={(e) => setScopeFilter(e.target.value as EventScope)}>
                <option value="all">All scopes</option>
                <option value="tool">Tool events only</option>
                <option value="task">Task events only</option>
              </Select>
              <Select value={typeFilter} onChange={(e) => setTypeFilter(e.target.value)}>
                {eventTypes.map((type) => (
                  <option key={type} value={type}>{type === 'all' ? 'All event types' : type}</option>
                ))}
              </Select>
              <Select value={timeFilter} onChange={(e) => setTimeFilter(e.target.value as TimeFilter)}>
                <option value="24h">Last 24 hours</option>
                <option value="7d">Last 7 days</option>
                <option value="30d">Last 30 days</option>
                <option value="all">All time</option>
              </Select>
              <Input
                placeholder="Search event payload..."
                value={searchFilter}
                onChange={(e) => setSearchFilter(e.target.value)}
              />
              <Button variant="outline" onClick={exportCsv} disabled={filteredEvents.length === 0}>
                Export CSV
              </Button>
            </div>
          </CardHeader>
          <CardContent>
            <div className="table-wrap">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Time</TableHead>
                    <TableHead>Type</TableHead>
                    <TableHead>Attributes</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {filteredEvents.map((event) => (
                    <TableRow key={event.id}>
                      <TableCell>{new Date(event.timestamp).toLocaleString()}</TableCell>
                      <TableCell><EventBadge type={event.type} /></TableCell>
                      <TableCell><code className="event-json">{JSON.stringify(event.attributes)}</code></TableCell>
                    </TableRow>
                  ))}
                  {filteredEvents.length === 0 ? (
                    <TableRow>
                      <TableCell colSpan={3}>No events match the current filters.</TableCell>
                    </TableRow>
                  ) : null}
                </TableBody>
              </Table>
            </div>
          </CardContent>
        </Card>
      </main>
    </div>
  );
}

function MetricCard({ label, value }: { label: string; value: string }) {
  return (
    <Card>
      <CardHeader>
        <CardDescription>{label}</CardDescription>
        <CardTitle className="metric-value">{value}</CardTitle>
      </CardHeader>
    </Card>
  );
}

function StatRow({ label, value }: { label: string; value: string | number }) {
  return (
    <div className="stat-row">
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

function FunnelBar({ label, value, max }: { label: string; value: number; max: number }) {
  const width = Math.max(6, (value / max) * 100);
  return (
    <div className="funnel-row">
      <div className="funnel-head">
        <span>{label}</span>
        <strong>{value}</strong>
      </div>
      <div className="funnel-track">
        <div className="funnel-fill" style={{ width: `${width}%` }} />
      </div>
    </div>
  );
}

function EventBadge({ type }: { type: string }) {
  const lower = type.toLowerCase();
  if (lower.includes('failed') || lower.includes('denied') || lower.includes('incident')) {
    return <Badge variant="warning">{type}</Badge>;
  }
  if (lower.includes('succeeded') || lower.includes('authorized') || lower.includes('captured')) {
    return <Badge variant="success">{type}</Badge>;
  }
  return <Badge>{type}</Badge>;
}

function toCsvCell(raw: unknown): string {
  const value = String(raw ?? '');
  const escaped = value.replaceAll('"', '""');
  return `"${escaped}"`;
}
