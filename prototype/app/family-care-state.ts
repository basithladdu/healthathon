import { isValidAppointmentDate } from './appointment-state.ts';

export const FAMILY_TASK_KINDS = ['Appointment', 'Transport', 'Medicines', 'Home care', 'Paperwork', 'Family support', 'Question'] as const;
export type FamilyTaskKind = (typeof FAMILY_TASK_KINDS)[number];
export type CareTaskRole = 'patient' | 'family' | 'doctor';
export type CareTaskAssignee = { id: string; name: string; role: CareTaskRole };
export type FamilyTask = {
  id: string;
  patientId: string;
  title: string;
  kind: FamilyTaskKind;
  owner: string;
  due: string;
  createdBy: string;
  completedBy: string | null;
  appointmentId?: string;
  createdById?: string;
  assignedToId?: string;
  ownerRole?: CareTaskRole;
  acceptedBy?: string;
  acceptedAt?: string;
};

export const INITIAL_FAMILY_TASKS: FamilyTask[] = [
  { id: 'family-demo-1', patientId: 'CANCER-20418', title: 'Arrange transport for the family meeting', kind: 'Transport', owner: 'Kavya Raghavan', due: '2026-08-28', createdBy: 'Kavya Raghavan', completedBy: null, appointmentId: 'APT-1010' },
  { id: 'family-demo-2', patientId: 'CANCER-20418', title: 'Bring the current prescription and medicine list', kind: 'Medicines', owner: 'Kavya Raghavan', due: '2026-08-28', createdBy: 'Kavya Raghavan', completedBy: null },
  { id: 'family-demo-3', patientId: 'CANCER-20418', title: 'Who should we contact between visits?', kind: 'Question', owner: 'Kavya Raghavan', due: '', createdBy: 'Kavya Raghavan', completedBy: null },
];

export function addFamilyTask(tasks: FamilyTask[], task: FamilyTask): FamilyTask[] {
  if (!task.patientId || !task.id || !task.title.trim() || !task.owner.trim() || !task.createdBy.trim()
    || !FAMILY_TASK_KINDS.includes(task.kind) || (task.due && !isValidAppointmentDate(task.due))
    || tasks.some((item) => item.id === task.id)
    || (task.appointmentId && tasks.some((item) => item.patientId === task.patientId && item.appointmentId === task.appointmentId && item.kind === task.kind && !item.completedBy))) return tasks;
  return [...tasks, { ...task, title: task.title.trim(), owner: task.owner.trim(), completedBy: null }];
}

export function setFamilyTaskCompleted(tasks: FamilyTask[], patientId: string, taskId: string, actor: string, complete: boolean): FamilyTask[] {
  if (!actor.trim()) return tasks;
  return tasks.map((task) => {
    if (task.patientId !== patientId || task.id !== taskId) return task;
    if (task.assignedToId && task.owner.trim().toLocaleLowerCase() !== actor.trim().toLocaleLowerCase()) return task;
    if (complete && task.assignedToId && task.createdById !== task.assignedToId && !task.acceptedBy) return task;
    return { ...task, completedBy: complete ? actor : null };
  });
}

export function editFamilyTask(tasks: FamilyTask[], patientId: string, updated: FamilyTask): FamilyTask[] {
  const previous = tasks.find((task) => task.patientId === patientId && task.id === updated.id);
  if (!previous || updated.patientId !== patientId) return tasks;
  const next: FamilyTask = {
    ...previous, assignedToId: updated.assignedToId, ownerRole: updated.ownerRole,
    acceptedBy: updated.acceptedBy, acceptedAt: updated.acceptedAt,
    title: updated.title.trim(), owner: updated.owner.trim(), kind: updated.kind, due: updated.due,
  };
  const assignmentChanged = next.assignedToId !== previous.assignedToId || next.owner !== previous.owner || next.ownerRole !== previous.ownerRole;
  const contentChanged = next.title !== previous.title || next.kind !== previous.kind || next.due !== previous.due;
  const changed = assignmentChanged || contentChanged || next.acceptedBy !== previous.acceptedBy || next.acceptedAt !== previous.acceptedAt;
  if (!changed) return tasks;
  const remaining = tasks.filter((task) => task !== previous);
  if (addFamilyTask(remaining, next) === remaining) return tasks;
  // A previous completion belongs to the previous wording, owner and schedule.
  return tasks.map((task) => task === previous ? { ...next, ...(contentChanged || assignmentChanged ? { completedBy: null } : {}), ...(assignmentChanged ? { acceptedBy: undefined, acceptedAt: undefined } : {}) } : task);
}

export function removeFamilyTask(tasks: FamilyTask[], patientId: string, taskId: string): FamilyTask[] {
  if (!tasks.some((task) => task.patientId === patientId && task.id === taskId)) return tasks;
  return tasks.filter((task) => task.patientId !== patientId || task.id !== taskId);
}

export function restoreFamilyTask(tasks: FamilyTask[], patientId: string, removed: FamilyTask): FamilyTask[] {
  if (removed.patientId !== patientId || tasks.some((task) => task.id === removed.id)) return tasks;
  // Completed tasks may coexist with a newer open task for the same appointment.
  const forValidation = removed.completedBy ? { ...removed, appointmentId: undefined } : removed;
  if (addFamilyTask(tasks, forValidation) === tasks) return tasks;
  return [...tasks, { ...removed }];
}
