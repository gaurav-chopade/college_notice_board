import { DepartmentApi } from '../../services/api';
import { INSTITUTION_LABEL } from '../../utils/format';
import SimpleCrud from './SimpleCrud';

/** Departments of both wings. Programme and semester count drive registration and the public pages. */
export default function Departments() {
  return (
    <SimpleCrud title="Departments" api={DepartmentApi}
      empty={{ name: '', code: '', description: '', institutionType: 'ENGINEERING', programName: '', semesterCount: null }}
      fields={[
        { key: 'institutionType', label: 'Institution', type: 'select', required: true, options: [['ENGINEERING', 'Engineering'], ['POLYTECHNIC', 'Polytechnic']] },
        { key: 'name', label: 'Name', required: true },
        { key: 'code', label: 'Code', required: true },
        { key: 'programName', label: 'Programme (e.g. B.Tech, Diploma)' },
        { key: 'semesterCount', label: 'Semesters', type: 'number', min: 1, max: 12 },
        { key: 'description', label: 'Description' },
      ]}
      columns={[
        { key: 'institutionType', label: 'Institution', render: (r) => INSTITUTION_LABEL[r.institutionType] },
        { key: 'name', label: 'Name' }, { key: 'code', label: 'Code' },
        { key: 'programName', label: 'Programme' }, { key: 'semesterCount', label: 'Semesters' },
        { key: 'description', label: 'Description' },
      ]} />
  );
}
